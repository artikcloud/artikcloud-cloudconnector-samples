package cloudconnector

import cloud.artik.cloudconnector.api_v1.*
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.joda.time.*
import org.scalactic.*
import scala.Option

import static java.net.HttpURLConnection.*

//@CompileStatic
class MyCloudConnector extends CloudConnector {
	static final CT_JSON = 'application/json'

	JsonSlurper slurper = new JsonSlurper()

	@Override
	def Or<RequestDef, Failure> signAndPrepare(Context ctx, RequestDef req, DeviceInfo info, Phase phase){
		switch (phase){
			case Phase.undef:
			case Phase.subscribe:
			case Phase.unsubscribe:
			case Phase.fetch:
			case Phase.refreshToken:
				new Good(req.addHeaders(["Api-Key": ctx.clientId(), "Authorization": "Bearer " + info.credentials.token]))
				break
			default:
				super.signAndPrepare(ctx, req, info, phase)
		}
	}

	@Override
	def Or<List<RequestDef>, Failure> subscribe(Context ctx, DeviceInfo info) {
		def hookJs = ["callback_url": ctx.parameters()['notificationCallback'] ,
			"shared_secret": ctx.parameters()['sharedSecret'] ,
			"subscription_type": "application.actigraphies"]
		def hookReq = new RequestDef("${ctx.parameters()['endPointUrl']}/v7.1/webhook/")
				.withMethod(HttpMethod.Post)
				.withContent(JsonOutput.toJson(hookJs), "application/json")
		def userReq = new RequestDef("${ctx.parameters()['endPointUrl']}/v7.1/user/self")
				.withMethod(HttpMethod.Get)
		return new Good([hookReq, userReq])
	}

	@Override
	def Or<Option<DeviceInfo>,Failure> onSubscribeResponse(Context ctx, RequestDef req,  DeviceInfo info, Response res) {
		switch (res.status) {
			case HTTP_OK:
				def json = slurper.parseText(res.content())
				def id = json?.id
				if (req.method() == HttpMethod.Post) {
					def userDataJson = slurper.parseText(info.userData().getOrElse("{}"))
					userDataJson.put("subscriptionId", id)
					new Good(Option.apply(info.withUserData(JsonOutput.toJson(userDataJson))))
				} else {
					new Good(Option.apply(info.withExtId(String.valueOf(id))))
				}
				break
			default:
				new Good(Option.apply(null))
		}
	}

	@Override
	Or<NotificationResponse, Failure> onNotification(Context ctx, RequestDef req) {
		def json = slurper.parseText(req.content())
		def type = json?.type ?: [""]
		switch (type[0]){
			case "application.actigraphies":
				def thirdPardyNotifications = json.collect { notification ->
					def userId = notification._links.user[0].id
					def deviceSelector = new ByExtId(userId)
					def urlEnd = notification._links.actigraphy[0].href
					new ThirdPartyNotification(deviceSelector, [
						new RequestDef("${ctx.parameters()['endPointUrl']}${urlEnd}").addQueryParams(['underArmourTs':notification.ts])
					])
				}
				new Good(new NotificationResponse(thirdPardyNotifications))
				break
			default:
				new Good(new NotificationResponse([]))
		}

	}

	@Override
	Or<List<Event>, Failure> onFetchResponse(Context ctx, RequestDef req, DeviceInfo info, Response res) {
		switch (res.status) {
			case HTTP_OK:
				def content = res.content.trim()
				ctx.debug(content)
				if (content == '' || content == 'OK') {
					ctx.debug("ignore response valid respond: '${res.content}'")
					return new Good([])
				} else if (res.contentType.startsWith(CT_JSON)) {
					def ts = new DateTime(req.queryParams()['underArmourTs']).getMillis()
					def json = slurper.parseText(content)
					def actigraphiesJson = json._embedded?.actigraphies ?: []
					def events = actigraphiesJson?.collectMany{ actigraphie ->
						extractMetrics(actigraphie, ts) +
								extractAggregates(actigraphie, ts) +
								extractWorkouts(actigraphie, ts)
					}
					return new Good(events)
				}
				return new Bad(new Failure("unsupported response ${res} ... ${res.contentType} .. ${res.contentType.startsWith(CT_JSON)}"))
			default:
				return new Bad(new Failure("http status : ${res.status} is not OK (${HTTP_OK})"))
		}
	}

	private def extractMetrics(jsonNode, long ts) {
		def type = "metric"
		def steps = jsonNode?.metrics?.steps ?: []
		def stepEvents = steps.collectMany { stepsInfo ->
			def startTime = new DateTime(stepsInfo.start_datetime_utc)
			def endTime = new DateTime(stepsInfo.end_datetime_utc)
			stepsInfo.time_series.epoch_values.collect { tsAndCount ->
				if (tsAndCount.size == 2) {
					def json = ["steps": (writeStep(type, tsAndCount[1], startTime, endTime) + ["timestamp": Math.round(tsAndCount[0] / 1000)]) ]
					def jsonStr = JsonOutput.toJson(denull(json))
					new Event(ts, jsonStr)
				}
			}
		}
		def sleeps = jsonNode?.metrics?.sleep ?: []
		def sleepEvents = sleeps.collectMany { sleepsInfo ->
			def startTime = new DateTime(sleepsInfo.start_datetime_utc)
			def endTime = new DateTime(sleepsInfo.end_datetime_utc)
			def totalSleep = sleepsInfo?.sum
			def lightSleep = sleepsInfo?.details?.light_sleep?.sum
			def deepSleep = sleepsInfo?.details?.deep_sleep?.sum
			def awake = sleepsInfo?.details?.awake?.sum

			def json = ["sleep": writeSleep(type, awake, totalSleep, lightSleep, deepSleep, startTime, endTime)]
			def jsonStr = JsonOutput.toJson(denull(json))
			[new Event(ts, jsonStr)]
		}
		stepEvents + sleepEvents
	}

	private def extractAggregates(jsonNode, long ts) {
		def type = "aggregates"
		def startTime = new DateTime(jsonNode.start_datetime_utc)
		def endTime = new DateTime(jsonNode.end_datetime_utc)
		def heartRateResting = jsonNode?.aggregates?.heart_rate_resting?.latest ?: -42
		if (heartRateResting != -42)
		{
			def json = ["heartrate": writeHeartRate(type, heartRateResting, startTime, endTime)]
			def jsonStr = JsonOutput.toJson(denull(json))
			[new Event(ts, jsonStr)]
		}
		else
			[]
	}

	private def extractWorkouts(jsonNode, long ts) {
		def type = "workout"
		def workouts = jsonNode?.workouts ?: []
		workouts.collectMany { workoutsInfo ->
			def startTime = new DateTime(workoutsInfo.start_datetime_utc)
			def endTime = new DateTime(workoutsInfo.end_datetime_utc)
			def steps = workoutsInfo?.aggregates?.details?.steps?.sum ?: 0
			if (steps != 0){
				def json = ["steps": writeStep(type, steps , startTime, endTime)]
				def jsonStr = JsonOutput.toJson(denull(json))
				[new Event(ts, jsonStr)]
			} else {
				[]
			}
		}
	}


	// write functions to write Js String

	private def writeSleep(String type, BigDecimal awake, BigDecimal sum, BigDecimal light, BigDecimal deep, DateTime start, DateTime end){
		return writeType(type) + writeDates(start, end) + ["awake":awake, "totalSleep":sum, "lightSleep":light, "deepSleep":deep]
	}

	private def writeHeartRate(String type, BigDecimal heartRateResting, DateTime start, DateTime end){
		return writeType(type) + writeDates(start, end) + ["resting": heartRateResting]
	}

	private def writeStep(String type, BigDecimal count, DateTime start, DateTime end){
		return writeType(type) + writeCount(count) + writeDates(start, end)
	}

	private def writeType(String type){
		return ["type": type]
	}

	private def writeCount(BigDecimal count){
		return ["count": count]
	}

	private def writeDates(DateTime start, DateTime end){
		return ["startDate":start.getMillis(), "endDate":end.getMillis()]
	}

	private def denull(obj) {
		if(obj instanceof java.util.Map) {
			obj.collectEntries {k, v ->
				(v != null)? [(k): denull(v)] : [:]
			}
		} else if(obj instanceof java.util.Collection) {
			obj.collect { denull(it) }.findAll { it != null }
		} else {
			obj
		}
	}
}
