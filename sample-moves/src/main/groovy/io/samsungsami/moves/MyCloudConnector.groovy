package io.samsungsami.moves

import org.scalactic.*
import org.joda.time.format.DateTimeFormat
import org.joda.time.*
import groovy.transform.CompileStatic
import groovy.transform.ToString
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import com.samsung.sami.cloudconnector.api.*
import static java.net.HttpURLConnection.*

//@CompileStatic
class MyCloudConnector extends CloudConnector {
	static final mdateFormat = DateTimeFormat.forPattern("yyyy-MM-dd").withZoneUTC()
	static final queryParams = ["timeZone": "UTC"]
	static final receivedDateFormat = DateTimeFormat.forPattern("yyyyMMdd'T'HHmmssZ").withZoneUTC().withOffsetParsed()
	static final requestDateFormat = DateTimeFormat.forPattern("yyyyMMdd").withZoneUTC()
	static final reasonToFetchSummaryData = ["DataUpload"]

	JsonSlurper slurper = new JsonSlurper()

	def summaryEndpoint(String date) {
		"https://api.moves-app.com/api/1.1/user/summary/daily/" + date
	}

	@Override
	def Or<NotificationResponse, Failure> onNotification(Context ctx, RequestDef inReq) {
		def json = slurper.parseText(inReq.content)
		def extId = json.userId.toString()
		def storyLineFiltered = json.storylineUpdates.findAll{ reasonToFetchSummaryData.contains(it.reason) }
		def datesFromStoryLines = storyLineFiltered.collect { e ->
			//We try to recover a valid Date from the storyLine event, and use it to fetch summary data.
			String dtStr = (
				(e.endTime)?e.endTime:
				(e.startTime)?e.startTime:
				(e.lastSegmentStartTime)?e.lastSegmentStartTime:
				null
			)
			DateTime dt = (dtStr != null) ?DateTime.parse(dtStr, receivedDateFormat): DateTime.now()
			requestDateFormat.print(dt)
		}.unique()
		def requestsToDo = datesFromStoryLines.collect{ dateStr ->
			new RequestDef(summaryEndpoint(dateStr)).withQueryParams(queryParams)
		}
		new Good(new NotificationResponse([new ThirdPartyNotification(new ByExternalDeviceId(extId), requestsToDo)]))
	}

	@Override
	def Or<RequestDef, Failure> fetch(Context ctx, RequestDef req, DeviceInfo info) {
		new Good(req.addHeaders(["Authorization": "Bearer " + info.credentials.token]))
	}

	def isSameDay(DateTime d1, DateTime d2) {
		(d1.year== d2.year) && (d1.dayOfYear ==  d2.dayOfYear)
	}
	def isToday(DateTime d) {
		isSameDay(d, DateTime.now())
	}
	def getTimestampOfTheEndOfTheDay(DateTime date) {
		date.minusMillis((date.millisOfDay().get() + 1 )).plusDays(1)
	}
	/**
	 * Since we recover the summary data of the day, we want to have a meaningful timestamp from source:
	 * If the date is not today : the day is finished. We set the source timestamp to the last second of this past day.
	 * If the date is today : the day is not finished, data can continue to evolve for the day, we set the timestamp to now()
	 */
	def getTimestampFromDate(DateTime date, DateTimeZone dtz = DateTimeZone.UTC) {
		def now = new DateTime(dtz).toDateTime(dtz)
		def returnedDate = isSameDay(date, now)? now : getTimestampOfTheEndOfTheDay(date)
		returnedDate.getMillis()
	}
	def extractSummaryNotification(jsonNode, long ts) {
		if (jsonNode.summary) {
			jsonNode.summary.collect {js -> new Event(ts, "{\"summary\":" + JsonOutput.toJson(js) + "}")}
		} else {
			[]
		}
	}

	def extractCaloriesIdle(jsonNode, long ts) {
		if (jsonNode.caloriesIdle) {
			[new Event(ts,"{\"caloriesIdle\":" + JsonOutput.toJson(jsonNode.caloriesIdle) + "}")]
		} else {
			[]
		}
	}

	@Override
	def Or<List<Event>, Failure> onFetchResponse(Context ctx, RequestDef req, DeviceInfo info, Response res) {
		switch(res.status) {
			case HTTP_OK:
				def content = res.content.trim()
				if (content == "") {
					ctx.debug("ignore response valid respond: '${res.content}'")
					return new Good(Empty.list())
				} else if (res.contentType.startsWith("application/json")) {
					def json = slurper.parseText(content)
					def events = json.collectMany { jsData ->
						def ts = (jsData.date)? getTimestampFromDate(DateTime.parse(jsData.date, requestDateFormat)): ctx.now()
						extractSummaryNotification(jsData, ts) + extractCaloriesIdle(jsData, ts)
					}
					return new Good(events)
				}
				return new Bad(new Failure("unsupported response ${res} ... ${res.contentType} .. ${res.contentType.startsWith("application/json")}"))
			default:
				return new Bad(new Failure("http status : ${res.status} is not OK (${HTTP_OK})"))
		}
	}

}
