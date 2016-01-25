package io.samsungsami.fitbit

import spock.lang.*
import scala.Option
import org.joda.time.*
import com.samsung.sami.cloudconnector.api_v1.*
import groovy.json.JsonSlurper
import utils.FakeContext
import static utils.Tools.*

class MyCloudConnectorSpec extends Specification {

	def sut = new MyCloudConnector()
	def parser = new JsonSlurper()
	def apiEndpoint = "https://api.fitbit.com/1/user/-/"
	def ctx = new FakeContext() {
		long now() { new DateTime(1970, 1, 17, 0, 0, DateTimeZone.UTC).getMillis() }
		Map<String, String> parameters(){
			["baseUrl":apiEndpoint]
		}
	}
	def device = new DeviceInfo("deviceId", Option.apply(null), new Credentials(AuthType.OAuth1, "", "1j0v33o6c5b34cVPqIiB_M2LYb_iM5S9Vcy7Rx7jA2630pK7HIjEXvJoiE8V5rRF", Empty.option(), Option.apply("bearer"), ctx.scope(), Empty.option()), ctx.cloudId(), Empty.option())

	def "Fetch user timezone on subscription"(){
		when:
		def profileReq = new RequestDef(apiEndpoint + "profile.json").withMethod(HttpMethod.Get).withContent("", "application/x-www-form-urlencoded")
		def profileResp = new Response(HttpURLConnection.HTTP_OK, "application/x-www-form-urlencoded", readFile(this, "profile.json"))
		def res = sut.onSubscribeResponse(ctx, profileReq, device, profileResp)
		then:
		res.isGood()
		res.get() == Option.apply(device.withUserData("America/Los_Angeles"))
	}

	def "signAndPrepare should remove scope from body and query params when refreshing a token"() {
		when:
			def refreshTokenReq = new RequestDef(apiEndpoint)
					.withBodyParams(["test1": "1", "scope" : "scope to be removed", "test2": "2"])
					.withQueryParams(["test1": "1", "scope" : "scope to be removed", "test2": "2"])
			def res = sut.signAndPrepare(ctx, refreshTokenReq, device, Phase.refreshToken)
		then:
			res.isGood()
			res.get().bodyParams == ["test1": "1", "test2": "2"]
			res.get().queryParams == ["test1": "1", "test2": "2"]
	}

	def "computeAuthHeader should compute the good hash (Oauth2 basic header)"() {
		when:
		//the Base64 encoded string, Y2xpZW50X2lkOmNsaWVudCBzZWNyZXQ=, is decoded as "client_id:client secret"
		def expectedResult =  "Y2xpZW50X2lkOmNsaWVudCBzZWNyZXQ="
		def res = sut.computeAuthHeader("client_id", "client secret")
		then:
		res == expectedResult
	}

	def "build requests on notification"(){
		when:
		def req = new RequestDef("").withMethod(HttpMethod.Post).withContent(readFile(this, "apiNotification.json"), "application/json")
		def res = sut.onNotification(ctx, req)
		then:
		res.isGood()
		res.get() == new NotificationResponse([
				new ThirdPartyNotification(new BySamiDeviceId("id1"), [new RequestDef(apiEndpoint + "foods/log/date/2010-03-01.json").withHeaders(["remember_date": "2010-03-01"]),]),
				new ThirdPartyNotification(new BySamiDeviceId("id2"), [
						new RequestDef(apiEndpoint + "activities/date/2011-03-01.json").withHeaders(["remember_date": "2011-03-01"]),
						new RequestDef(apiEndpoint + "activities/heart/date/2011-03-01/1d.json").withHeaders(["remember_date": "2011-03-01"])
				]),
		])
	}

	def "fetch activity without user timezone"() {
		when:
		def req = new RequestDef(apiEndpoint + "activities/date/2011-03-01.json").withHeaders(["remember_date": "2011-03-01"])
		def resp = new Response(HttpURLConnection.HTTP_OK, "application/json", readFile(this, "apiActivities.json"))
		def res = sut.onFetchResponse(ctx, req, device, resp)
		then:
		res.isGood()
		//1299023999999s = Tue, 01 Mar 2011 23:59:59 GMT in UTC
		def timestamp=1299023999999L
		def events = [new Event(timestamp, readFile(this, "events/activities.json"))]
		cmpEvents(res.get(), events)
	}

	def "fetch activity with custom user timezone (America/Los_Angeles)"() {
		when:
		def req = new RequestDef(apiEndpoint + "activities/date/2011-03-01.json").withHeaders(["remember_date": "2011-03-01"])
		def resp = new Response(HttpURLConnection.HTTP_OK, "application/json", readFile(this, "apiActivities.json"))
		def res = sut.onFetchResponse(ctx, req, device.withUserData("America/Los_Angeles"), resp)
		then:
		res.isGood()
		//1299052799999s =  Wed, 02 Mar 2011 07:59:59 GMT = Tue, 01 Mar 2011 23:59:59 America/Los_Angeles
		def timestamp=1299052799999L
		def events = [new Event(timestamp, readFile(this, "events/activities.json"))]
		cmpEvents(res.get(), events)
	}

	def "fetch food"() {
		when:
		def req = new RequestDef(apiEndpoint + "foods/log/date/2010-03-01.json").withHeaders(["remember_date": "2010-03-01"])
		def resp = new Response(HttpURLConnection.HTTP_OK, "application/json", readFile(this, "apiFoods.json"))
		def res = sut.onFetchResponse(ctx, req, device, resp)
		then:
		res.isGood()
		//1267487999999L=Mon, 01 Mar 2010 23:59:59 GMT
		def timestamp=1267487999999L
		def events = [new Event(timestamp, readFile(this, "events/foods.json"))]
		cmpEvents(res.get(), events)
	}

}
