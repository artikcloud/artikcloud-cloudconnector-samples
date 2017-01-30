package cloudconnector

import cloud.artik.cloudconnector.api_v1.*
import org.joda.time.*
import org.scalactic.*
import scala.Option
import spock.lang.*
import utils.*

import static java.net.HttpURLConnection.*
import static utils.Tools.*

class MyCloudConnectorSpec extends Specification {

	def sut = new MyCloudConnector()
	def ctx = new FakeContext() {
		Map parameters() {
			["redirectUri":"http://foo.com"]
		}
	}
	def extId = "23138311640030064"
	def apiEndpoint = "https://api.moves-app.com/api/1.1"
	def info = new DeviceInfo("deviceId", Option.apply(extId), new Credentials(AuthType.OAuth2, "", "1j0v33o6c5b34cVPqIiB_M2LYb_iM5S9Vcy7Rx7jA2630pK7HIjEXvJoiE8V5rRF", Empty.option(), Option.apply("bearer"), ctx.scope(), Empty.option()), ctx.cloudId(), Empty.option())


	def "refresh token"() {
		when:
		def req = new RequestDef("").withMethod(HttpMethod.Get)
		def res = sut.signAndPrepare(ctx, req, info, Phase.refreshToken)
		def expectedReq = new RequestDef("").withQueryParams([
				"response_type": "refresh_token",
				"UserID": info.extId().getOrElse(""),
				"client_id": ctx.clientId(),
				"client_secret": ctx.clientSecret(),
				"redirect_uri":"http://foo.com"
		])

		then:
		res.isGood() == true
		res.get() == expectedReq
	}

	def "build requests on notification"() {
		when:
		def did = "05dffbe0dd*****"
		def req = new RequestDef("").withMethod(HttpMethod.Post).withContent(readFile(this, "notification.json"), "application/json")
		def res = sut.onNotification(ctx, req)
		def param1_2 = ["client_id"    : "clientId",
					  "client_secret": "clientSecret",
					  "sc"           : null,
					  "sv"           : null,
					  "locale"       : "default",
					  "start_time"   : "1267451101",
					  "end_time"     : "1267451102"]
		def param3_4 = param1_2 + ["start_time"   : "1267515597", "end_time"     : "1267515598"]
		def param5_6_7 = param1_2 + ["start_time"   : "1267451311", "end_time"     : "1267451312"]
		def param8 = param1_2 + ["start_time"   : "1267515597", "end_time"     : "1267515598"]
		def expectedReq1 = new RequestDef("null/user/"+ did + "/activity.json").withMethod(HttpMethod.Get).withQueryParams(param1_2)
		def expectedReq2 = new RequestDef("null/user/"+ did + "/bp.json").withMethod(HttpMethod.Get).withQueryParams(param1_2)
		def expectedReq3 = new RequestDef("null/user/"+ did + "/food.json").withMethod(HttpMethod.Get).withQueryParams(param3_4)
		def expectedReq4 = new RequestDef("null/user/"+ did + "/glucose.json").withMethod(HttpMethod.Get).withQueryParams(param3_4)
		def expectedReq5 = new RequestDef("null/user/"+ did + "/sleep.json").withMethod(HttpMethod.Get).withQueryParams(param5_6_7)
		def expectedReq6 = new RequestDef("null/user/"+ did + "/spo2.json").withMethod(HttpMethod.Get).withQueryParams(param5_6_7)
		def expectedReq7 = new RequestDef("null/user/"+ did + "/sport.json").withMethod(HttpMethod.Get).withQueryParams(param5_6_7)
		def expectedReq8 = new RequestDef("null/user/"+ did + "/weight.json").withMethod(HttpMethod.Get).withQueryParams(param8)

		then:
		res.isGood()
		res.get().thirdPartyNotifications.size == 8
		res.get().thirdPartyNotifications[0] == new ThirdPartyNotification(new ByExtId(did), [expectedReq1])
		res.get().thirdPartyNotifications[1] == new ThirdPartyNotification(new ByExtId(did), [expectedReq2])
		res.get().thirdPartyNotifications[2] == new ThirdPartyNotification(new ByExtId(did), [expectedReq3])
		res.get().thirdPartyNotifications[3] == new ThirdPartyNotification(new ByExtId(did), [expectedReq4])
		res.get().thirdPartyNotifications[4] == new ThirdPartyNotification(new ByExtId(did), [expectedReq5])
		res.get().thirdPartyNotifications[5] == new ThirdPartyNotification(new ByExtId(did), [expectedReq6])
		res.get().thirdPartyNotifications[6] == new ThirdPartyNotification(new ByExtId(did), [expectedReq7])
		res.get().thirdPartyNotifications[7] == new ThirdPartyNotification(new ByExtId(did), [expectedReq8])
	}

	def "create events from fetch response"() {
		when:
		def msg = readFile(this, "activity.json")
		def fetchedResponse = new Response(HttpURLConnection.HTTP_OK, "application/json", msg)
		def res = sut.onFetchResponse(ctx, null, null , fetchedResponse)
		def ts=1355129100000L
		def expectedEvents = [
						new Event(ts, '''{"Calories":109,"DataID":"e34032089471451b926a6a4*****","DataSource":"FromDevice","DistanceTraveled":0.36088,"LastChangeTime":1392261858,"Lat":19.579758571265153,"Lon":86.49735491466585,"MDate":1355157900,"Note":"","Steps":694,"TimeZone":"+0800"}''')
						]

		then:
		res.isGood()
		res.get()[0] == expectedEvents[0]
		res.get().size() == expectedEvents.size()
		res.get() == expectedEvents
	}
}
