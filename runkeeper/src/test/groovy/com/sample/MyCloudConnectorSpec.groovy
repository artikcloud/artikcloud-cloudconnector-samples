package com.sample

import static java.net.HttpURLConnection.*

//import org.junit.Test
import spock.lang.*
import org.scalactic.*
import scala.Option
import org.joda.time.format.DateTimeFormat
import org.joda.time.*
import com.samsung.sami.cloudconnector.api_v1.*
import utils.FakeContext
import groovy.json.JsonSlurper
import groovy.json.JsonOutput

class MyCloudConnectorSpec extends Specification {

		def sut = new MyCloudConnector()
		def ctx = new FakeContext()

		def info = new DeviceInfo(
			"did",
			Empty.option(), 
			new Credentials(AuthType.OAuth2, "", "", Empty.option(), Option.apply("bearer"), ctx.scope(), Empty.option()), 
			ctx.cloudId(), 
			Empty.option()
		)
		def cloudUserData = '{"userId": 1, "weight": "/weight", "sleep": "/sleep"}'
		def infoWithUserData = info.withUserData(JsonOutput.toJson([
			"uris": ["sleep": "/sleep", "weight": "/weight"],
			"lastPolls": ["sleep": ctx.now(), "weight": ctx.now()]
		]))

		def "store user data on onSubscribeResponse"() {
			when:
			def req = new RequestDef("foo")
			def res = new Response(200, "application/vnd.com.runkeeper.User+json", cloudUserData)
			def result = sut.onSubscribeResponse(ctx, req, info, res)

			then:
			result.isGood()
			result.get() == Option.apply(infoWithUserData)
		}

		def "poll weight data on getWeightData action"() {
			when:
			def action = new ActionDef(Empty.option(), "did", ctx.now(), "getWeightData", "")
			def result = sut.onAction(ctx, action, infoWithUserData)

			then:
			result.isGood()
			result.get() == new ActionResponse([
				new ActionRequest(
					new BySamiDeviceId("did"),
					[new RequestDef("${ctx.parameters().endpoint}/weight").withMethod(HttpMethod.Get)]
				)	
			]) 
		}

		def "push weight data to SAMI on onFetchResponse when new data and update lastPoll"() {
			when:
			def req = new RequestDef("${ctx.parameters().endpoint}/weight").withMethod(HttpMethod.Get)
			def body = JsonOutput.toJson([
				"items": [
					["timestamp": "Fri, 1 Jan 2016 00:10:00", "weight": 50], 
					["timestamp": "Fri, 1 Jan 2016 00:09:55", "free_mass": 10, "bmi": 5],
					["timestamp": "Fri, 1 Jan 2016 00:09:30", "weight": 49],
				]
			])
			def res = new Response(200, "application/vnd.com.runkeeper.WeightSetFeed+json", body)
			def result = sut.onFetchResponse(ctx, req, infoWithUserData, res)

			then:
			result.isGood()
			def ts1 = DateTime.parse("Fri, 1 Jan 2016 00:10:00", MyCloudConnector.timestampFormat).getMillis()
			def ts2 = DateTime.parse("Fri, 1 Jan 2016 00:09:55", MyCloudConnector.timestampFormat).getMillis()
			def updatedUserData = JsonOutput.toJson([
				"lastPolls": ["sleep": ctx.now(), "weight": ts1], // take last ts1 (ctx.now() is fixed in this test)
				"uris": ["sleep": "/sleep", "weight": "/weight"]
			])

			result.get() == [
				new Event(ts1, JsonOutput.toJson(["weight": 50])), 
				new Event(ts2, JsonOutput.toJson(["bmi": 5, "free_mass": 10])), 
				new Event(ctx.now(), updatedUserData, EventType.user)
			]
		}

		def "push nothing when no new data on onFetchResponse"() {
			when:
			def req = new RequestDef("${ctx.parameters().endpoint}/weight").withMethod(HttpMethod.Get)
			def body = JsonOutput.toJson([
				"items": [
					["timestamp": "Fri, 1 Jan 2016 00:09:30", "weight": 49]
				]
			])
			def res = new Response(200, "application/vnd.com.runkeeper.WeightSetFeed+json", body)
			def result = sut.onFetchResponse(ctx, req, infoWithUserData, res)

			then:
			result.isGood()
			result.get() == []
		}
		
}
