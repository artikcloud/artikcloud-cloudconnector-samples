package io.samsungsami.philips

import cloud.artik.cloudconnector.api_v1.*
import org.scalactic.*
import scala.Option
import groovy.json.JsonOutput
import spock.lang.*
import utils.*

import static utils.Tools.*

class MyCloudConnectorSpec extends Specification {
	static final String did = "aDid"
	static final String structId = "aStructId"
	static final String endpoint = "https://developer-api.philips.com"
	static final String endpointSetTemp = "$endpoint/devices/thermostats/$did"
	static final String endpointSetHomes = "$endpoint/structures/$structId"

	def sut = new MyCloudConnector()
	def ctx = new FakeContext() {
		Map parameters() {
			["productType":"thermostat",
			 "endpoint": endpoint]
		}
	}
	def info = new DeviceInfo("deviceId", Option.apply(did),
			new Credentials(AuthType.OAuth2, "", "1j0v33o6c5b34cVPqIiB_M2LYb_iM5S9Vcy7Rx7jA2630pK7HIjEXvJoiE8V5rRF", Empty.option(), Option.apply("bearer"), ctx.scope(), Empty.option()),
			ctx.cloudId(),
			Empty.option()
	)

	def "on action fetch data or set parameter"() {
		when:
		def ts = 42L
		def globalReq = new RequestDef(endpoint)
		def setTempReq = new RequestDef(endpointSetTemp).withMethod(HttpMethod.Put)
		def setHomeReq = new RequestDef(endpointSetHomes).withMethod(HttpMethod.Put)
		def tempActionParam = '''{"deviceId":"''' + did + '''","temp":42}'''
		def homeActionParam = '''{"structureId":"''' +structId +'''"}'''
		def globalAction = new ActionDef(Option.apply("sdid"), "ddid", ts, "getAllData", "")
		def tempCAction = new ActionDef(Option.apply("sdid"), "ddid", ts, "setTemperature", tempActionParam)
		def tempFAction = new ActionDef(Option.apply("sdid"), "ddid", ts, "setTemperatureInFahrenheit", tempActionParam)
		def homeAction = new ActionDef(Option.apply("sdid"), "ddid", ts, "setHome", homeActionParam)
		def awayAction = new ActionDef(Option.apply("sdid"), "ddid", ts, "setAway", homeActionParam)
		def res = [sut.onAction(ctx, globalAction, info)] +
				[sut.onAction(ctx, tempCAction, info)] +
				[sut.onAction(ctx, tempFAction, info)] +
				[sut.onAction(ctx, homeAction, info)] +
				[sut.onAction(ctx, awayAction, info)]
		def expectedEvents = [
				new ActionRequest([globalReq]),
				new ActionRequest([setTempReq.withContent(JsonOutput.toJson(["target_temperature_c":42]), "application/json")]),
				new ActionRequest([setTempReq.withContent(JsonOutput.toJson(["target_temperature_f":42]), "application/json")]),
				new ActionRequest([setHomeReq.withContent(JsonOutput.toJson(["away": "home"]), "application/json")]),
				new ActionRequest([setHomeReq.withContent(JsonOutput.toJson(["away": "away"]), "application/json")])
		].collect{ actionReq -> new Good( new ActionResponse([actionReq]))}

		then:
		res[0].isGood()
		res.size() == expectedEvents.size()
		res[0].get() == expectedEvents[0].get()
		res[1].get() == expectedEvents[1].get()
		res[2].get() == expectedEvents[2].get()
		res[3].get() == expectedEvents[3].get()
		res[4].get() == expectedEvents[4].get()
		res.size() == expectedEvents.size()
		res == expectedEvents
	}


	def "do not create events from fetch response without enougth data"() {
		when:
		def fetchedResponse = new Response(HttpURLConnection.HTTP_OK, "application/json", '{"target_temperature_c": 22.5}')
		def req = new RequestDef(endpoint)
		def res = sut.onFetchResponse(ctx, req, info, fetchedResponse)
		def ts = 1461234567890L
		def expectedEvents = Empty.list()
		then:
		res.isGood()
		res.get() == expectedEvents
	}

	def "create events from fetch response (single device)"() {
		when:
		def msg = readFile(this, "thermostatExample.json")
		def fetchedResponse = new Response(HttpURLConnection.HTTP_OK, "application/json", msg)
		def req = new RequestDef(endpoint)
		def res = sut.onFetchResponse(ctx, req, info, fetchedResponse)
		def ts = 10L
		def expectedEvents = [
				new Event(ts, '''{"ambient_temperature_c":24,"away_temperature_high_c":24,"away_temperature_low_c":9,"can_cool":false,"can_heat":true,"device_id":"aDeviceId","fan_timer_active":false,"fan_timer_timeout":"1970-01-01T00:00:00.000Z","has_fan":false,"has_leaf":false,"humidity":35,"hvac_mode":"heat","hvac_state":"off","is_online":true,"is_using_emergency_heat":false,"last_connection":"2016-04-19T10:02:40.028Z","name":"Office","name_long":"Office Thermostat","structure":{"away":"home","name":"Paris Office","structure_id":"aDeviecId"},"structure_id":"aStructureId","target_temperature_c":21.5,"target_temperature_high_c":24,"target_temperature_low_c":20}'''),
		]
		then:
		res.isGood()
		res.get()[0] == expectedEvents[0]
		res.get().size() == expectedEvents.size()
		res.get() == expectedEvents
	}
}