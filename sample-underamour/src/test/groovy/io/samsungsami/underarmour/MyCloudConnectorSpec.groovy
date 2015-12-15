package io.samsungsami.underarmour

import com.samsung.sami.cloudconnector.api_v1.*
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
/*
    def "accept OAuth2"() {
  		when:
  		def req = new RequestDef("https://foo/cloudconnector/dt00/thirdpartynotification")
  		def res = sut.onNotification(ctx, req)
  		then:
  		res.isBad()
    }


    def "reject Notification without NotificationId"() {
  		when:
  		def req = new RequestDef("https://foo/cloudconnector/dt00/thirdpartynotification")
  		def res = sut.onNotification(ctx, req)
  		then:
  		res.isBad()
    }
*/

	def "accept userId subscripe"() {
		when:
		def req = new RequestDef("${ctx.parameters()['endPointUrl']}/v7.1/user/self")
				.withMethod(HttpMethod.Get)
		def info = new DeviceInfo("", Option.apply(null), null, "", Option.apply(null))
		def resp = new Response(HttpURLConnection.HTTP_OK, "application/json", '''{"id":42}''')
		def res =sut.onSubscribeResponse(ctx, req, info, resp)

		then:
		res.isGood()
		res.get() == Option.apply(info.withExtId("42"))
	}

	def "accept webhook subscripe"() {
		when:
		def hookJs = ["callback_url": ctx.parameters()['notificationCallback'] ,
					  "shared_secret": ctx.parameters()['sharedSecret'] ,
					  "subscription_type": "application.actigraphies"]
		def req = new RequestDef("${ctx.parameters()['endPointUrl']}/v7.1/webhook/")
				.withMethod(HttpMethod.Post)
				.withContent(JsonOutput.toJson(hookJs), "application/json")
		def info = new DeviceInfo("", Option.apply(null), null, "", Option.apply(null))
		def resp = new Response(HttpURLConnection.HTTP_OK, "application/json", '''{"id":42}''')
		def res =sut.onSubscribeResponse(ctx, req, info, resp)

		then:
		res.isGood()
		res.get() == Option.apply(info.withUserData(JsonOutput.toJson(["subscriptionId":42])))
	}


    def "accept valid Notification"() {
  		when:
		def ts = "2015-03-01T23:12:20.687025+00:00"
  		def did = 'dxxxx'
		def euid= 'uxxx'
  		def req = new RequestDef('https://foo/cloudconnector/dt00/thirdpartynotification')
  			.withContent('''[{
				"type": "application.actigraphies",
				"_links": {
					"user": [{
						"href": "/v7.1/user/1/",
						"id": "''' + euid + '''"
					}],
					"actigraphy": [{
						"href": "/v7.1/actigraphy/?start_date=2015-03-01&end_date=2015-03-01"
					}]
				},
				"ts": "''' + ts + '''",
				"object_id": "''' + did + '''"
			}]''', "application/json")
  		def res = sut.onNotification(ctx, req)
		def execetedReq = new RequestDef("${ctx.parameters()['endPointUrl']}/v7.1/actigraphy/?start_date=2015-03-01&end_date=2015-03-01").addQueryParams(['underArmourTs':ts])

  		then:
    		res.isGood()
			res.get().thirdPartyNotifications[0].selector == new ByExternalDeviceId(euid)
			res.get().thirdPartyNotifications[0].requestsOfData[0] == execetedReq
			res.get() == new NotificationResponse([
				new ThirdPartyNotification(new ByExternalDeviceId(euid), [execetedReq])
			])
    }

	def "fetch empty actigraphies"() {
		//readFile(this, "lala.json")
		when:
		def ts = "2015-03-01T23:12:20.687025+00:00"
		def req = new RequestDef(null).addQueryParams(["underArmourTs":ts])
		def fetchedResponse = new Response(HttpURLConnection.HTTP_OK, "application/json", '''{
					"_embedded":{
					"actigraphies":[ ]
				}
			}''')
		def res = sut.onFetchResponse(ctx, req, null , fetchedResponse)

		then:
		res.isGood()
		res.get() == []
	}

	def "fetch actigraphies with metrics"() {
		when:
		def ts = "2015-03-01T23:12:20.687025+00:00"
		def msg = readFile(this, "actigraphiesExemple.json")
		def req = new RequestDef(null).addQueryParams(["underArmourTs":ts])
		def fetchedResponse = new Response(HttpURLConnection.HTTP_OK, "application/json", '''
					'''+ msg + '''
			}''')
		def res = sut.onFetchResponse(ctx, req, null , fetchedResponse)
		def expectedSleepEvent = new Event(1425251540687,'''{"sleep":{"type":"metric", "awake":550, "sleep-sum":23424, "light-sleep":13319, "deep-sleep":10105"startDate":1397610566000, "endDate":1397635690000}}''')
		def expectedHeartRate = [
				new Event(1425251540687,'''{"heartrate":{"type":"aggregates", "heartrate-resting":54, "startDate":1397631600000, "endDate":1397717999000}}'''),
				new Event(1425251540687,'''{"heartrate":{"type":"aggregates", "heartrate-resting":50, "startDate":1397718000000, "endDate":1397804399000}}''')
		]
		def expectedEvents = [
				new Event(1425251540687,'''{"steps":{"type":"metric", "count":54, "startDate":1397631600000, "endDate":1397718000000, "timestamp":1397633400}}'''),
				new Event(1425251540687,'''{"steps":{"type":"metric", "count":0, "startDate":1397631600000, "endDate":1397718000000, "timestamp":1397634300}}'''),
				new Event(1425251540687,'''{"steps":{"type":"metric", "count":0, "startDate":1397631600000, "endDate":1397718000000, "timestamp":1397635200}}'''),
				new Event(1425251540687,'''{"steps":{"type":"metric", "count":24, "startDate":1397631600000, "endDate":1397718000000, "timestamp":1397656800}}'''),
				expectedSleepEvent,
				expectedHeartRate[0],
				new Event(1425251540687,'''{"steps":{"type":"metric", "count":135, "startDate":1397718000000, "endDate":1397804400000, "timestamp":1397749500}}'''),
				new Event(1425251540687,'''{"steps":{"type":"metric", "count":0, "startDate":1397718000000, "endDate":1397804400000, "timestamp":1397750400}}'''),
				new Event(1425251540687,'''{"steps":{"type":"metric", "count":0, "startDate":1397718000000, "endDate":1397804400000, "timestamp":1397751300}}'''),
				new Event(1425251540687,'''{"steps":{"type":"metric", "count":42, "startDate":1397718000000, "endDate":1397804400000, "timestamp":1397798100}}'''),
				expectedHeartRate[1]
		]

		then:
		res.isGood()
		res.get()[1] == expectedEvents[1]
		res.get()[2] == expectedEvents[2]
		res.get()[3] == expectedEvents[3]
		res.get()[4] == expectedEvents[4]
		res.get()[5] == expectedEvents[5]
		res.get()[6] == expectedEvents[6]
		res.get()[7] == expectedEvents[7]
		res.get()[8] == expectedEvents[8]
		res.get()[9] == expectedEvents[9]
		res.get()[10] == expectedEvents[10]
		res.get().size() == expectedEvents.size()
		res.get() == expectedEvents
	}

	def "fetch actigraphies with workouts"() {
		when:
		def ts = "2015-03-01T23:12:20.687025+00:00"
		def msg = readFile(this, "actigraphiesWorkouts.json")
		def req = new RequestDef(null).addQueryParams(["underArmourTs":ts])
		def fetchedResponse = new Response(HttpURLConnection.HTTP_OK, "application/json", '''
					'''+ msg + '''
			}''')
		def res = sut.onFetchResponse(ctx, req, null , fetchedResponse)
		def expectedEvents = [
				new Event(1425251540687,'''{"steps":{"type":"workout", "count":13729, "startDate":1450085457000, "endDate":1450085697000}}'''),
				new Event(1425251540687,'''{"steps":{"type":"workout", "count":140, "startDate":1450086496000, "endDate":1450086676000}}'''),
				new Event(1425251540687,'''{"steps":{"type":"workout", "count":1541, "startDate":1450089342000, "endDate":1450089582000}}'''),
				new Event(1425251540687,'''{"steps":{"type":"workout", "count":8266, "startDate":1450089760000, "endDate":1450090120000}}'''),
				new Event(1425251540687,'''{"steps":{"type":"workout", "count":1541, "startDate":1450089853000, "endDate":1450090153000}}''')
		]

		then:
		res.isGood()
		res.get()[1] == expectedEvents[1]
		res.get()[2] == expectedEvents[2]
		res.get()[3] == expectedEvents[3]
		res.get()[4] == expectedEvents[4]
		res.get().size() == expectedEvents.size()
		res.get() == expectedEvents
	}
}
