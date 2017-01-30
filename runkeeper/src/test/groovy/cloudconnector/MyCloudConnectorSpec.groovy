package cloudconnector

import static java.net.HttpURLConnection.*

//import org.junit.Test
import spock.lang.*
import org.scalactic.*
import scala.Option
import org.joda.time.format.DateTimeFormat
import org.joda.time.*
import cloud.artik.cloudconnector.api_v1.*
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
		def cloudUserData = '{"userId": 1, "weight": "/weight", "sleep": "/sleep", "fitness_activities": "/fitness"}'
		def infoWithUserData = info.withUserData(JsonOutput.toJson(
			["fitness_activities": "/fitness", "sleep": "/sleep", "userId" : 1, "weight": "/weight"])
		)

		def "store user data on onSubscribeResponse"() {
			when:
			def req = new RequestDef("foo")
			def res = new Response(200, "application/vnd.com.runkeeper.User+json", cloudUserData)
			def result = sut.onSubscribeResponse(ctx, req, info, res)

			then:
			result.isGood()
			result.get() == Option.apply(infoWithUserData)
		}

		def "pull weight data on getWeightData action"() {
			when:
			def action = new ActionDef(Empty.option(), "did", ctx.now(), "getWeightData", '{"nbHoursToPull": 1}')
			def result = sut.onAction(ctx, action, infoWithUserData)

			then:
			result.isGood()
			result.get() == new ActionResponse([
				new ActionRequest(
					[new RequestDef("${ctx.parameters().endpoint}/weight")
						.withMethod(HttpMethod.Get)
						.withQueryParams(["samiPullStartTs" : (ctx.now() - 3600*1000).toString(), "samiPullEndTs": ctx.now().toString()])
					]
				)	
			]) 
		}

		def "push weight data to ARTIK Cloud on onFetchResponse"() {
			when:
			def req = new RequestDef("${ctx.parameters().endpoint}/weight")
						.withMethod(HttpMethod.Get)
						.withQueryParams(["samiPullStartTs" : (ctx.now() - 3600*1000).toString(), "samiPullEndTs": ctx.now().toString()])
			def body = JsonOutput.toJson([
				"items": [
					["timestamp": "Fri, 1 Jan 2016 09:44:00", "weight": 50], 
					["timestamp": "Fri, 1 Jan 2016 09:40:00", "free_mass": 10, "bmi": 5],
					["timestamp": "Fri, 1 Jan 2016 08:30:00", "weight": 49]
				]
			])
			def res = new Response(200, "application/vnd.com.runkeeper.WeightSetFeed+json", body)
			def result = sut.onFetchResponse(ctx, req, infoWithUserData, res)

			then:
			result.isGood()
			def ts1 = DateTime.parse("Fri, 1 Jan 2016 09:44:00", MyCloudConnector.timestampFormat).getMillis()
			def ts2 = DateTime.parse("Fri, 1 Jan 2016 09:40:00", MyCloudConnector.timestampFormat).getMillis()

			result.get() == [
				new Event(ts1, JsonOutput.toJson(["weight": 50])), 
				new Event(ts2, JsonOutput.toJson(["bmi": 5, "free_mass": 10]))
			]
		}

		def "push nothing when no new data on onFetchResponse"() {
			when:
			def req = new RequestDef("${ctx.parameters().endpoint}/weight")
						.withMethod(HttpMethod.Get)
						.withQueryParams(["samiPullStartTs" : (ctx.now() - 3600*1000).toString(), "samiPullEndTs": ctx.now().toString()])
			def body = JsonOutput.toJson([
				"items": [
					["timestamp": "Fri, 1 Jan 2016 08:30:00", "weight": 49]
				]
			])
			def res = new Response(200, "application/vnd.com.runkeeper.WeightSetFeed+json", body)
			def result = sut.onFetchResponse(ctx, req, infoWithUserData, res)

			then:
			result.isGood()
			result.get() == []
		}

		def "push fitness data to ARTIK Cloud on onFetchResponse"() {
			when:
			def req = new RequestDef("${ctx.parameters().endpoint}/fitness")
						.withMethod(HttpMethod.Get)
						.withQueryParams(["samiPullStartTs" : (ctx.now() - 3600*1000).toString(), "samiPullEndTs": ctx.now().toString()])
			def body = JsonOutput.toJson([
				"items": [
					["start_time": "Fri, 1 Jan 2016 09:44:00", "heart_rate": [["heart_rate": 70], ["heart_rate": 80], ["heart_rate": 75]], "total_distance": 40]
				]
			])
			def res = new Response(200, "application/vnd.com.runkeeper.FitnessSetFeed+json", body)
			def result = sut.onFetchResponse(ctx, req, infoWithUserData, res)

			then:
			result.isGood()
			def ts1 = DateTime.parse("Fri, 1 Jan 2016 09:44:00", MyCloudConnector.timestampFormat).getMillis()

			result.get() == [
				new Event(ts1, JsonOutput.toJson(["max_heart_rate": 80, "min_heart_rate": 70, "total_distance": 40]))
			]
		}

		def "push fitness data to ARTIK Cloud on onFetchResponse with no heart rate"() {
			when:
			def req = new RequestDef("${ctx.parameters().endpoint}/fitness")
						.withMethod(HttpMethod.Get)
						.withQueryParams(["samiPullStartTs" : (ctx.now() - 3600*1000).toString(), "samiPullEndTs": ctx.now().toString()])
			def body = JsonOutput.toJson([
				"items": [
					["start_time": "Fri, 1 Jan 2016 09:44:00", "total_distance": 40]
				]
			])
			def res = new Response(200, "application/vnd.com.runkeeper.FitnessSetFeed+json", body)
			def result = sut.onFetchResponse(ctx, req, infoWithUserData, res)

			then:
			result.isGood()
			def ts1 = DateTime.parse("Fri, 1 Jan 2016 09:44:00", MyCloudConnector.timestampFormat).getMillis()

			result.get() == [
				new Event(ts1, JsonOutput.toJson(["total_distance": 40]))
			]
		}

		def "push fitness data to ARTIK Cloud on onFetchResponse with utc_offset"() {
			when:
			def req = new RequestDef("${ctx.parameters().endpoint}/fitness")
						.withMethod(HttpMethod.Get)
						.withQueryParams(["samiPullStartTs" : (ctx.now() - 3600*1000).toString(), "samiPullEndTs": ctx.now().toString()])
			def body = JsonOutput.toJson([
				"items": [
					["start_time": "Fri, 1 Jan 2016 10:40:00", "total_distance": 40, "utc_offset": 1]
				]
			])
			def res = new Response(200, "application/vnd.com.runkeeper.FitnessSetFeed+json", body)
			def result = sut.onFetchResponse(ctx, req, infoWithUserData, res)

			then:
			result.isGood()
			def ts1 = DateTime.parse("Fri, 1 Jan 2016 09:40:00", MyCloudConnector.timestampFormat).getMillis()

			result.get() == [
				new Event(ts1, JsonOutput.toJson(["total_distance": 40]))
			]
		}

		def "push fitness data to ARTIK Cloud on onFetchResponse with utc_offset and duration"() {
			when:
			def req = new RequestDef("${ctx.parameters().endpoint}/fitness")
						.withMethod(HttpMethod.Get)
						.withQueryParams(["samiPullStartTs" : (ctx.now() - 3600*1000).toString(), "samiPullEndTs": ctx.now().toString()])
			def body = JsonOutput.toJson([
				"items": [
					["start_time": "Fri, 1 Jan 2016 10:00:00", "total_distance": 40, "utc_offset": 1, "duration": 30 * 60]
				]
			])
			def res = new Response(200, "application/vnd.com.runkeeper.FitnessSetFeed+json", body)
			def result = sut.onFetchResponse(ctx, req, infoWithUserData, res)

			then:
			result.isGood()
			def ts1 = DateTime.parse("Fri, 1 Jan 2016 09:30:00", MyCloudConnector.timestampFormat).getMillis()

			result.get() == [
				new Event(ts1, JsonOutput.toJson(["duration": 30 * 60, "total_distance": 40]))
			]
		}
		
}
