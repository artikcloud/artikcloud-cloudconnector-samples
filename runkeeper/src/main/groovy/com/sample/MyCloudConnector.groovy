// Sample CloudConnector, that can be used as a boostrap to write a new CloudConnector.
// Every code is commented, because everything is optional.
// The class can be named as you want no additional import allowed
// See the javadoc/scaladoc of com.samsung.sami.cloudconnector.api_v1.CloudConnector
package com.sample

import static java.net.HttpURLConnection.*

import org.scalactic.*
import org.joda.time.format.DateTimeFormat
import org.joda.time.*
import scala.Option
import groovy.transform.CompileStatic
import groovy.transform.ToString
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import com.samsung.sami.cloudconnector.api_v1.*
import org.joda.time.format.ISODateTimeFormat

//@CompileStatic
class MyCloudConnector extends CloudConnector {
	static final timestampFormat = DateTimeFormat.forPattern("EEE, d MMM yyyy HH:mm:ss").withZoneUTC()

	JsonSlurper slurper = new JsonSlurper()

	@Override
	Or<RequestDef, Failure> signAndPrepare(Context ctx, RequestDef req, DeviceInfo info, Phase phase) {
		ctx.debug('phase: ' + phase)
		//if (phase == Phase.refreshToken) {
		//   //TODO change some params
		//}
		return new Good(req.addHeaders(['Authorization':'Bearer ' + info.credentials.token]))
	}

	@Override
	Or<List<RequestDef>, Failure> subscribe(Context ctx, DeviceInfo info) {
		def req = new RequestDef("${ctx.parameters().endpoint}/user").withMethod(HttpMethod.Get)
		new Good([req])
	}

	@Override
	Or<Option<DeviceInfo>,Failure> onSubscribeResponse(Context ctx, RequestDef req,  DeviceInfo info, Response res) {
		 if (res.status == HTTP_OK) {
			def userData = slurper.parseText(res.content.trim()) 
			return new Good(Option.apply(info.withUserData(JsonOutput.toJson(userData))))
		} else {
			return new Bad(new Failure("onSubscribeResponse response status ${res.status} $res"))
		}
	}

	@Override
	Or<List<RequestDef>, Failure> unsubscribe(Context ctx, DeviceInfo info) {
		def req = new RequestDef("${ctx.parameters().appEndpoint}/de-authorize")
			.withMethod(HttpMethod.Post)
			.withBodyParams(["access_token" : info.credentials.token])
			.withContent("", "application/x-www-form-urlencoded")

		new Good([req])
	}

    @Override
    Or<Option<DeviceInfo>,Failure> onUnsubscribeResponse(Context ctx, RequestDef req,  DeviceInfo info, Response res) {
    	if (res.status >= HTTP_OK && res.status < 400) {
    		ctx.debug("Unsubscribe successful")
    		new Good(Empty.option())
    	} else {
    		new Bad(new Failure("Unsubscribe failed with status code ${res.status} $res"))
    	}
    }

	@Override
	Or<RequestDef, Failure> fetch(Context ctx, RequestDef req, DeviceInfo info) {
		new Good(req.addQueryParams(["pageSize" : "100"]))
	}

	@Override
	Or<List<Event>, Failure> onFetchResponse(Context ctx, RequestDef req, DeviceInfo info, Response res) {
		ctx.debug("onFetchResponse response: $res")

		def userData = info.userData.map { data -> slurper.parseText(data) }.getOrElse(null)
		if (userData == null) {
			return new Bad(new Failure("Can not get userData"))
		}
		def startTs = req.queryParams.get("samiPullStartTs").toLong()
		def endTs = req.queryParams.get("samiPullEndTs").toLong()
		if (startTs == null) {
			return new Bad(new Failure("Can not get samiPullStartTs"))	
		}
		if (endTs == null) {
			return new Bad(new Failure("Can not get samiPullEndTs"))		
		}

		if (res.status == HTTP_OK) {
			def data = slurper.parseText(res.content.trim())
			def events = data.items.collect { item ->
				def tsStr = (userData.fitness_activities != null && req.url.endsWith(userData.fitness_activities)) ? item.start_time : item.timestamp
				def ts = DateTime.parse(tsStr, timestampFormat).getMillis()
				item.remove("timestamp")
				item.remove("start_time")
				item.remove("uri")

				if (userData.fitness_activities != null && req.url.endsWith(userData.fitness_activities)) {
					def heartRates = item.get("heart_rate").collect { heartRateObj -> heartRateObj.heart_rate }
					item.put("max_heart_rate", Collections.max(heartRates))
					item.put("min_heart_rate", Collections.min(heartRates))
					item.remove("heart_rate")
				}

				new Event(ts, JsonOutput.toJson(item))
			}.findAll { event -> 
				event.ts >= startTs && event.ts < endTs
			}
			ctx.debug("Pushing events $events")
			return new Good(events)
		} else {
			return new Bad(new Failure("onFetchResponse response status ${res.status} $res"))
		}
	}

	@Override
	Or<ActionResponse, Failure> onAction(Context ctx, ActionDef action, DeviceInfo info) {
		ctx.debug("onAction: $action")

		def userData = info.userData.map { data -> slurper.parseText(data) }.getOrElse(null)
		if (userData == null) {
			return new Bad(new Failure("Can not get userData"))
		}

		def actionName = action.name
		def actionParams = slurper.parseText(action.params)
		def pullStartTime = action.ts - (actionParams.nbHoursToPull * 3600 * 1000)
		def uris = null
		if (actionName == "getWeightData") uris = [userData.weight]
		else if (actionName == "getFitnessData") uris = [userData.fitness_activities]
		else if (actionName == "getSleepData") uris = [userData.sleep]
		else if (actionName == "getDiabeteData") uris = [userData.diabetes]
		else if (actionName == "getGeneralMeasurements") uris = [userData.general_measurements]
		else if (actionName == "getAllData") {
			uris = [userData.weight, userData.fitness_activities, userData.sleep, userData.diabetes, userData.general_measurements]
		}

		if (uris != null) {
			def reqs = uris.collect { uri ->
				new RequestDef("${ctx.parameters().endpoint}$uri")
					.withMethod(HttpMethod.Get)
					.addQueryParams(["samiPullStartTs": pullStartTime.toString(), "samiPullEndTs": action.ts.toString()])
			}
			return new Good(new ActionResponse([new ActionRequest(reqs)]))
		} else {
			return new Bad(new Failure("Action ${action.name} does not match with an URI."))
		}
	}

}
