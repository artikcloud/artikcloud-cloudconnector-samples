package cloudconnector

import cloud.artik.cloudconnector.api_v1.*
import groovy.json.JsonOutput
import org.joda.time.*
import org.scalactic.*
import spock.lang.*
import utils.*
import scala.Option

import static java.net.HttpURLConnection.*
import static utils.Tools.*

class MyCloudConnectorSpec extends Specification {

    def sut = new MyCloudConnector()

    def ctx = new FakeContext(){
		Map parameters() {
			["endPointUrl":"http://api.ua.com"]
		}
	}		
	def fetchReq = { String url, String action ->
		new RequestDef(url).withMethod(HttpMethod.Get).withQueryParams(["action": action, "startdate": 1452530582, "enddate": 1452530583])
	}
	def mesureReq = fetchReq(sut.MEASURE_ENDPOINT, "getmeas")
	def sleepReq = fetchReq(sut.SLEEP_ENDPOINT, "get")
	def activityReq = fetchReq(sut.ACTIVITY_ENDPOINT, "getactivity").withQueryParams(["action": "getactivity", "date": "2014-06-08"])
	def mesureActivityReq = fetchReq(sut.MEASURE_ENDPOINT, "getmeas").addQueryParams(["startdate": "1402185600", "enddate": "1402185600"])

	def "accept userId subscribe"() {
		when:
		def info = new DeviceInfo("", Option.apply(null), null, "", Option.apply(null))
		def res = sut.subscribe(ctx, info)
		def exectedRequest = new RequestDef("https://wbsapi.withings.net/notify").withMethod(HttpMethod.Get)
		def expectedReq1a = exectedRequest.withQueryParams([ "action":"revoke",	"appli":"1",
															"callbackurl":"null?samiDeviceId=&appliId=1",
															"comment":"Notifications to Body Scale for Samsung ARTIK Cloud Platform"])
		def expectedReq2a = exectedRequest.withQueryParams([ "action":"revoke",	"appli":"4",
															"callbackurl":"null?samiDeviceId=&appliId=4",
															"comment":"Notifications to Blood pressure monitor for Samsung ARTIK Cloud Platform"])
		def expectedReq3a = exectedRequest.withQueryParams([ "action":"revoke",	"appli":"16",
															"callbackurl":"null?samiDeviceId=&appliId=16",
															"comment":"Notifications to Withings pulse for Samsung ARTIK Cloud Platform"])
		def expectedReq4a = exectedRequest.withQueryParams([ "action":"revoke",	"appli":"44",
															"callbackurl":"null?samiDeviceId=&appliId=44",
															"comment":"Notifications to Sleep monitor for Samsung ARTIK Cloud Platform"])
		def expectedReq1b = exectedRequest.withQueryParams([ "action":"subscribe",	"appli":"1",
															"callbackurl":"null012345/thirdpartynotifications?samiDeviceId=&appliId=1",
															"comment":"Notifications to Body Scale for Samsung ARTIK Cloud Platform"])
		def expectedReq2b = exectedRequest.withQueryParams([ "action":"subscribe",	"appli":"4",
															"callbackurl":"null012345/thirdpartynotifications?samiDeviceId=&appliId=4",
															"comment":"Notifications to Blood pressure monitor for Samsung ARTIK Cloud Platform"])
		def expectedReq3b = exectedRequest.withQueryParams([ "action":"subscribe",	"appli":"16",
															"callbackurl":"null012345/thirdpartynotifications?samiDeviceId=&appliId=16",
															"comment":"Notifications to Withings pulse for Samsung ARTIK Cloud Platform"])
		def expectedReq4b = exectedRequest.withQueryParams([ "action":"subscribe",	"appli":"44",
															"callbackurl":"null012345/thirdpartynotifications?samiDeviceId=&appliId=44",
															"comment":"Notifications to Sleep monitor for Samsung ARTIK Cloud Platform"])

		then:
		res.isGood()
		res.get()[0] == expectedReq1a
		res.get()[1] == expectedReq2a
		res.get()[2] == expectedReq3a
		res.get()[3] == expectedReq4a
		res.get()[4] == expectedReq1b
		res.get()[5] == expectedReq2b
		res.get()[6] == expectedReq3b
		res.get() == [expectedReq1a, expectedReq2a, expectedReq3a, expectedReq4a, expectedReq1b, expectedReq2b, expectedReq3b, expectedReq4b]
	}

	def "Receive notification"() {
		when:
		def did = "did"
		def params = { String appId -> ["samiDeviceId": did, "appliId": appId, "date": "2014-06-08"] }
		def req = { String appId -> new RequestDef('https://foo/cloudconnector/dt00/thirdpartynotification')
					.withQueryParams(params(appId))
					.withContent('''{"startdate":[1452530582], "enddate":[1452530583], "userid":[6188063]}''', "application/json")
				}

		def results = ["1", "4", "16", "44"].collect { sut.onNotification(ctx, req(it)) }

		then:
		results[0].isGood()
		results[0].get().thirdPartyNotifications[0].selector == new ByDid(did)
		results[0].get().thirdPartyNotifications[0].requestsOfData == [mesureReq]
		results[0].get() == new NotificationResponse([new ThirdPartyNotification(new ByDid(did), [mesureReq])])
		results[1].get().thirdPartyNotifications[0].requestsOfData == [mesureReq]
		results[1].get() == new NotificationResponse([new ThirdPartyNotification(new ByDid(did), [mesureReq])])
		results[2].get().thirdPartyNotifications[0].requestsOfData[0] == activityReq
		results[2].get().thirdPartyNotifications[0].requestsOfData[1] == mesureActivityReq
		results[2].get().thirdPartyNotifications[0].requestsOfData == [activityReq, mesureActivityReq]
		results[2].get() == new NotificationResponse([new ThirdPartyNotification(new ByDid(did), [activityReq, mesureActivityReq])])
		results[3].get().thirdPartyNotifications[0].requestsOfData == [sleepReq, mesureReq]
		results[3].get() == new NotificationResponse([new ThirdPartyNotification(new ByDid(did), [sleepReq, mesureReq])])
	}

	def "fetch data after from activity notification"() {
		when:
		def msg = readFile(this, "activityMeasure.json")
		def fetchedResponse = new Response(HttpURLConnection.HTTP_OK, "application/json", msg)
		def res = sut.onFetchResponse(ctx, activityReq, null , fetchedResponse)
		def expectedEvents = [
				new Event(1381010400000L,"""{"measuregrp":{"calories":530.79,"date":"2013-10-06","distance":7439.44,"elevation":808.24,"intense":0,"moderate":960,"soft":9240,"steps":10233,"timezone":"Europe/Berlin"},"category":"measure/getactivity"}"""),
				new Event(1381096800000L,"""{"measuregrp":{"calories":351.71,"date":"2013-10-07","distance":5015.6,"elevation":50.78,"moderate":1860,"soft":17580,"steps":6027,"timezone":"Europe/Berlin"},"category":"measure/getactivity"}"""),
				new Event(1381183200000L,"""{"measuregrp":{"calories":164.25,"date":"2013-10-08","distance":2127.73,"elevation":33.68,"intense":540,"moderate":1080,"soft":5880,"steps":2552,"timezone":"Europe/Berlin"},"category":"measure/getactivity"}""")
		]
		then:
		res.isGood()
		res.get()[0] == expectedEvents[0]
		res.get()[1] == expectedEvents[1]
		res.get()[2] == expectedEvents[2]
		res.get().size() == expectedEvents.size()
		res.get() == expectedEvents
	}

	def "fetch data after from blood pressure notification"() {
		when:
		def msg = readFile(this, "bodyMeasures.json")
		def fetchedResponse = new Response(HttpURLConnection.HTTP_OK, "application/json", msg)
		def res = sut.onFetchResponse(ctx, mesureReq, null , fetchedResponse)
		def expectedEvents = [
				new Event(1222930968000L,"""{"measuregrp":{"attrib":0,"category":1,"date":1222930968,"grpid":2909,"measures":[{"type":1,"unit":-3,"value":79300},{"type":5,"unit":-1,"value":652},{"type":6,"unit":-1,"value":178},{"type":8,"unit":-3,"value":14125}]},"category":"measure/getmeas"}"""),
				new Event(1222930968000L,"""{"measuregrp":{"attrib":0,"category":1,"date":1222930968,"grpid":2908,"measures":[{"type":4,"unit":-2,"value":173}]},"category":"measure/getmeas"}"""),
		]
		then:
		res.isGood()
		res.get()[0] == expectedEvents[0]
		res.get()[1] == expectedEvents[1]
		res.get().size() == expectedEvents.size()
		res.get() == expectedEvents
	}

	def "fetch data after from sleep notification"() {
		when:
		def msg = readFile(this, "sleepMeasures.json")
		def fetchedResponse = new Response(HttpURLConnection.HTTP_OK, "application/json", msg)
		def res = sut.onFetchResponse(ctx, sleepReq, null , fetchedResponse)
		def expectedEvents = [
				new Event(1417550400000L,"""{"measuregrp":{"enddate":1417551300,"startdate":1417550400,"state":0},"category":"sleep/get"}"""),
				new Event(1417551360000L,"""{"measuregrp":{"enddate":1417552320,"startdate":1417551360,"state":1},"category":"sleep/get"}"""),
				new Event(1417552380000L,"""{"measuregrp":{"enddate":1417554000,"startdate":1417552380,"state":3},"category":"sleep/get"}""")
		]
		then:
		res.isGood()
		println(res.get()[0])
		println(res.get()[1])
		println(res.get()[2])
		res.get()[0] == expectedEvents[0]
		res.get()[1] == expectedEvents[1]
		res.get()[2] == expectedEvents[2]
		res.get().size() == expectedEvents.size()
		res.get() == expectedEvents
	}
}
