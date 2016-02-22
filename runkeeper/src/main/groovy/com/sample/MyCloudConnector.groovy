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

	// -----------------------------------------
	// SUBSCRIPTION
	// -----------------------------------------

	@Override
	Or<List<RequestDef>, Failure> subscribe(Context ctx, DeviceInfo info) {
		def req = new RequestDef("${ctx.parameters().endpoint}/user").withMethod(HttpMethod.Get)
		new Good([req])
	}

	@Override
	Or<Option<DeviceInfo>,Failure> onSubscribeResponse(Context ctx, RequestDef req,  DeviceInfo info, Response res) {
		 if (res.status == HTTP_OK) {
			def json = slurper.parseText(res.content.trim()) 
			json.remove("userId")

			def now = ctx.now()
			def lastPolls = json.collectEntries { kind, uri -> [kind, now] }

			def dataToSave = JsonOutput.toJson(["uris" : json, "lastPolls" : lastPolls])
			return new Good(Option.apply(info.withUserData(dataToSave)))
		} else {
			return new Bad(new Failure("onSubscribeResponse response status ${res.status} $res"))	
		}
	}

	@Override
	Or<List<RequestDef>, Failure> unsubscribe(Context ctx, DeviceInfo info) {
		def req = new RequestDef("${ctx.parameters().endpoint}/de-authorize")
			.withMethod(HttpMethod.Post)
			.withBodyParams(["access_token" : info.credentials.token])
			.withContent("", "application/x-www-form-urlencoded")

		new Good([req])
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

		if (res.status == HTTP_OK) {
			if (req.url.endsWith(userData.uris.weight)) {
				def data = slurper.parseText(res.content.trim())
				def lastPoll = userData.lastPolls.weight
				def events = data.items.collect { item ->
					def ts = DateTime.parse(item.timestamp, timestampFormat).getMillis()
					item.remove("timestamp")
					item.remove("uri")
					new Event(ts, JsonOutput.toJson(item))
				}.findAll { event -> event.ts > lastPoll }

				if (events[0] != null) {
					// getting most recent ts
					ctx.debug("Pulling new data...")
					userData.lastPolls.weight = events[0].ts
					def lastPollEvent = new Event(ctx.now(), JsonOutput.toJson(userData), EventType.user)
					return new Good(events + lastPollEvent)
				} else {
					ctx.debug("No new data to pull")
					return new Good(events)
				}
			} else {
				return new Bad(new Failure("Unknown onFetchResponse req url ${req.url}"))
			}
		} else {
			return new Bad(new Failure("onFetchResponse response status ${res.status} $res"))
		}
	}

	@Override
	Or<ActionResponse, Failure> onAction(Context ctx, ActionDef action, DeviceInfo info) {
		def userData = info.userData.map { data -> slurper.parseText(data) }.getOrElse(null)
		if (userData == null) {
			return new Bad(new Failure("Can not get userData"))
		}

		switch (action.name) {
			case "getWeightData":
				def uri = userData.uris.weight
				if (uri != null) {
					def req = new RequestDef("${ctx.parameters().endpoint}$uri").withMethod(HttpMethod.Get)
					return new Good(new ActionResponse([new ActionRequest(new BySamiDeviceId(info.did), [req])]))
				} else {
					return new Bad(new Failure("URI for weight is not stored in userData"))
				}

			default:        
				return new Bad(new Failure("Unknown action: ${action.name}"))
		}
	}

}
