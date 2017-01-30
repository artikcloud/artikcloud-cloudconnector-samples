package cloudconnector

import cloud.artik.cloudconnector.api_v1.*
import org.scalactic.*
import scala.Option
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import spock.lang.*
import utils.*

import static utils.Tools.*

class MyCloudConnectorSpec extends Specification {
	static final String dId = "peyiJNo0IldT2YlIVtYaGQ"
	static final String dName= "Hallway (upstairs)"
	static final String sId1 = "VqFabWH21nwVyd4RWgJgNb292wa7hG_dUwo2i2SG7j3-BOLY0BA4sw"
	static final String sId2 = "wrFabWH21nwVyd4RWgJgNb292wa7hG_dUwo2i2SG7j3-BOLY0BA4tx"
	static final String sName = "Home"
	static final String endpoint = "https://developer-api.nest.com"
	static final String endpointSetTemp = "$endpoint/devices/thermostats/$dId"
	static final String endpointSetHomes = "$endpoint/structures/$sId1"

	def JsonSlurper slurper = new JsonSlurper()
	def sut = new MyCloudConnector()
	def ctx = new FakeContext() {
		Map parameters() {[
			"productType":"thermostat",
			"endpoint": endpoint
		]}
	}
	//generateName2IdsPairs(List objList, String mapName, String nameKey, String idKey)
	def thermostatExample = readFile(this, "thermostatExample.json")
	def jsonExample = slurper.parseText(thermostatExample.trim())
    def deviceList = jsonExample?.devices?.get("${ctx.parameters().productType}s")?.values() as List
    def structureList = jsonExample?.structures?.values() as List
    def userData = sut.generateName2IdsPairs(deviceList, 'devices', 'name', 'device_id') +
                	sut.generateName2IdsPairs(structureList, 'structures', 'name', 'structure_id')
    def userDataStr = '''{"devices":{"Hallway (upstairs)":["peyiJNo0IldT2YlIVtYaGQ"],"Nowhere (downstairs)":["qfyiJNo0IldT2YlIVtYaHR"]},"devicesLowerCase":{"hallway (upstairs)":["peyiJNo0IldT2YlIVtYaGQ"],"nowhere (downstairs)":["qfyiJNo0IldT2YlIVtYaHR"]},"structures":{"Home":["VqFabWH21nwVyd4RWgJgNb292wa7hG_dUwo2i2SG7j3-BOLY0BA4sw","wrFabWH21nwVyd4RWgJgNb292wa7hG_dUwo2i2SG7j3-BOLY0BA4tx"]},"structuresLowerCase":{"home":["VqFabWH21nwVyd4RWgJgNb292wa7hG_dUwo2i2SG7j3-BOLY0BA4sw","wrFabWH21nwVyd4RWgJgNb292wa7hG_dUwo2i2SG7j3-BOLY0BA4tx"]}}'''
	def info = new DeviceInfo("deviceId", Option.apply(dId),
			new Credentials(AuthType.OAuth2, "", "1j0v33o6c5b34cVPqIiB_M2LYb_iM5S9Vcy7Rx7jA2630pK7HIjEXvJoiE8V5rRF", Empty.option(), Option.apply("bearer"), ctx.scope(), Empty.option()),
			ctx.cloudId(),
			Option.apply(userDataStr)
	)

    def "combine and map"(){
        when:
        def goods = [new Good(1), new Good(2)]
        def goodsAndBad = [new Good(1), new Bad(new Failure("NaN")), new Good(2)]
        def plusTen = {x -> x + 10}
        then:
        sut.combine(goods) == new Good([1,2])
        sut.combine(goodsAndBad).isBad()
        sut.collectOnOr(goods[0], plusTen) == new Good(11)
    }

	def "do not create events from fetch response without enough data"() {
		when:
		def fetchedResponse = new Response(HttpURLConnection.HTTP_OK, "application/json", '{"target_temperature_c": 22.5}')
		def req = new RequestDef(endpointSetTemp)
		def res = sut.onFetchResponse(ctx, req, info, fetchedResponse)
		def ts = 1461234567890L
		def expectedEvents = Empty.list()
		then:
		res.isGood()
		res.get() == expectedEvents
	}

	def "create events from fetch response (single device) --- getAllData"() {
		when:
		def msg = readFile(this, "thermostatExample.json")
		def fetchedResponse = new Response(HttpURLConnection.HTTP_OK, "application/json", msg)
		def req = new RequestDef(endpoint).withHeaders(['X-Artik-Action': 'getAllData'])
		def res = sut.onFetchResponse(ctx, req, info, fetchedResponse)
		def ts = 10L
		def expectedEvents = [
			new Event(ts, '''{"ambient_temperature_c":24,"away_temperature_high_c":24,"away_temperature_low_c":9,"can_cool":false,"can_heat":true,"device_id":"aDeviceId","fan_timer_active":false,"fan_timer_timeout":"1970-01-01T00:00:00.000Z","has_fan":false,"has_leaf":false,"humidity":35,"hvac_mode":"heat","hvac_state":"off","is_online":true,"is_using_emergency_heat":false,"last_connection":"2016-04-19T10:02:40.028Z","name":"Office","name_long":"Office Thermostat","structure_id":"aStructureId","target_temperature_c":21.5,"target_temperature_high_c":24,"target_temperature_low_c":20}''', EventType.data, Option.apply('aDeviceId'))
			]
		then:
		res.isGood()
		res.get()[0] == expectedEvents[0]
		res.get().size() == expectedEvents.size()
		res.get() == expectedEvents
	}

	def "create events from fetch response (single device) --- synchronizeDevices"() {
		when:
		def msg = readFile(this, "thermostatExample.json")
		def fetchedResponse = new Response(HttpURLConnection.HTTP_OK, "application/json", msg)
		def req = new RequestDef(endpoint).withHeaders(['X-Artik-Action': 'synchronizeDevices'])
		def res = sut.onFetchResponse(ctx, req, info, fetchedResponse)
		def ts = 10L
		def expectedEvents = [
			new Event(ts, '''{"devices":{"Office":["aDeviceId"]},"devicesLowerCase":{"office":["aDeviceId"]},"structures":{"Paris Office":["aDeviecId"]},"structuresLowerCase":{"paris office":["aDeviecId"]}}''', EventType.user),
			new Event(ts, 'Office', EventType.createOrUpdateDevice, Option.apply('aDeviceId'))
			]
		then:
		res.isGood()
		res.get()[0] == expectedEvents[0]
		res.get().size() == expectedEvents.size()
		res.get() == expectedEvents
	}

	def "create events from fetch response (multiple devices) --- getAllData"() {
		when:
		def msg = readFile(this, "multiDevices.json")
		def fetchedResponse = new Response(HttpURLConnection.HTTP_OK, "application/json", msg)
		def req = new RequestDef(endpoint).withHeaders(['X-Artik-Action': 'getAllData'])
		def res = sut.onFetchResponse(ctx, req, info, fetchedResponse)
		def ts = 10L
		def expectedEvents = [
			new Event(ts, '''{"ambient_temperature_c":21.5,"away_temperature_high_c":21.5,"away_temperature_low_c":17.5,"can_cool":true,"can_heat":true,"device_id":"peyiJNo0IldT2YlIVtYaGQ","fan_timer_active":true,"fan_timer_timeout":"2016-10-31T23:59:59.000Z","has_fan":true,"has_leaf":true,"humidity":40,"hvac_mode":"heat","hvac_state":"heating","is_online":true,"is_using_emergency_heat":true,"last_connection":"2016-10-31T23:59:59.000Z","name":"Hallway (upstairs)","name_long":"Hallway Thermostat (upstairs)","structure_id":"VqFabWH21nwVyd4RWgJgNb292wa7hG_dUwo2i2SG7j3-BOLY0BA4sw","target_temperature_c":21.5,"target_temperature_high_c":21.5,"target_temperature_low_c":17.5}''', EventType.data, Option.apply('peyiJNo0IldT2YlIVtYaGQ')),
			new Event(ts,'''{"ambient_temperature_c":21.5,"away_temperature_high_c":21.5,"away_temperature_low_c":17.5,"can_cool":true,"can_heat":true,"device_id":"qfyiJNo0IldT2YlIVtYaHR","fan_timer_active":true,"fan_timer_timeout":"2016-10-31T23:59:59.000Z","has_fan":true,"has_leaf":true,"humidity":40,"hvac_mode":"heat","hvac_state":"heating","is_online":true,"is_using_emergency_heat":true,"last_connection":"2016-10-31T23:59:59.000Z","name":"Nowhere (downstairs)","name_long":"Nowhere Thermostat (downstairs)","structure_id":"VqFabWH21nwVyd4RWgJgNb292wa7hG_dUwo2i2SG7j3-BOLY0BA4sw","target_temperature_c":21.5,"target_temperature_high_c":21.5,"target_temperature_low_c":17.5}''', EventType.data, Option.apply('qfyiJNo0IldT2YlIVtYaHR'))
		]
		then:
		res.isGood()
		res.get()[0] == expectedEvents[0]
		res.get()[1] == expectedEvents[1]
		res.get().size() == expectedEvents.size()
		res.get() == expectedEvents
	}

	def "create events from fetch response (multiple devices) --- synchronizeDevices"() {
		when:
		def msg = readFile(this, "multiDevices.json")
		def fetchedResponse = new Response(HttpURLConnection.HTTP_OK, "application/json", msg)
		def req = new RequestDef(endpoint).withHeaders(['X-Artik-Action': 'synchronizeDevices'])
		def res = sut.onFetchResponse(ctx, req, info, fetchedResponse)
		def ts = 10L
		def expectedEvents = [
			new Event(ts, '''{"devices":{"Hallway (upstairs)":["peyiJNo0IldT2YlIVtYaGQ"],"Nowhere (downstairs)":["qfyiJNo0IldT2YlIVtYaHR"]},"devicesLowerCase":{"hallway (upstairs)":["peyiJNo0IldT2YlIVtYaGQ"],"nowhere (downstairs)":["qfyiJNo0IldT2YlIVtYaHR"]},"structures":{"Home":["VqFabWH21nwVyd4RWgJgNb292wa7hG_dUwo2i2SG7j3-BOLY0BA4sw","wrFabWH21nwVyd4RWgJgNb292wa7hG_dUwo2i2SG7j3-BOLY0BA4tx"]},"structuresLowerCase":{"home":["VqFabWH21nwVyd4RWgJgNb292wa7hG_dUwo2i2SG7j3-BOLY0BA4sw","wrFabWH21nwVyd4RWgJgNb292wa7hG_dUwo2i2SG7j3-BOLY0BA4tx"]}}''', EventType.user),
			new Event(ts, 'Hallway (upstairs)', EventType.createOrUpdateDevice, Option.apply('peyiJNo0IldT2YlIVtYaGQ')),
			new Event(ts, 'Nowhere (downstairs)', EventType.createOrUpdateDevice, Option.apply('qfyiJNo0IldT2YlIVtYaHR'))
		]
		then:
		res.isGood()
		res.get()[0] == expectedEvents[0]
		res.get()[1] == expectedEvents[1]
		res.get().size() == expectedEvents.size()
		res.get() == expectedEvents
	}

	static def json = { obj -> JsonOutput.toJson(obj) }
	static def req = { gStr, map, header -> 
		new RequestDef(gStr.toString())
			.withMethod(HttpMethod.Put)
			.withContent(JsonOutput.toJson(map), "application/json") 
			.withHeaders(['X-Artik-Action': header])
	}

	@Unroll
	def "valid onAction #actionName "(actionName, actionParams, expectedReqList) {
		expect:
		def ts = 42L
		def action = new ActionDef(Option.apply("sdid"), "ddid", ts, actionName, actionParams)
		def expected = new ActionResponse([new ActionRequest(expectedReqList)])
		def result = sut.onAction(ctx, action, info)

		result.isGood()
		result.get() == expected

		where:
		actionName									| actionParams							| expectedReqList
		"getAllData"								| ""									| [new RequestDef(endpoint).withHeaders(["X-Artik-Action": "getAllData"])]
		"setTemperatureByDeviceId"					| json([deviceId: dId, temp: 20])		| [req(endpointSetTemp, [target_temperature_c: 20], "setTemperatureByDeviceId")]
		"setTemperatureByDeviceName"				| json([deviceName: dName, temp: 20])	| [req(endpointSetTemp, [target_temperature_c: 20], "setTemperatureByDeviceName")]
		"setTemperatureInFahrenheitByDeviceName"	| json([deviceName: dName, temp: 50])	| [req(endpointSetTemp, [target_temperature_f: 50], "setTemperatureInFahrenheitByDeviceName")]
		"setTemperatureInFahrenheitByDeviceId"		| json([deviceId: dId, temp: 50])		| [req(endpointSetTemp, [target_temperature_f: 50], "setTemperatureInFahrenheitByDeviceId")]
		"setAwayByStructureName"					| json([structureName: sName]) 			| [req(endpointSetHomes, [away: "away"], "setAwayByStructureName"), req("$endpoint/structures/$sId2", [away: "away"], "setAwayByStructureName")]
		"setAwayByStructureId"						| json([structureId: sId1]) 			| [req(endpointSetHomes, [away: "away"], "setAwayByStructureId")]
		"setHomeByStructureName"					| json([structureName: sName])			| [req(endpointSetHomes, [away: "home"], "setHomeByStructureName"), req("$endpoint/structures/$sId2", [away: "home"], "setHomeByStructureName")]
		"setHomeByStructureId"						| json([structureId: sId1]) 			| [req(endpointSetHomes, [away: "home"], "setHomeByStructureId")]
		"setOffByDeviceId"							| json([deviceId: dId])					| [req(endpointSetTemp, [hvac_mode: "off"], "setOffByDeviceId")]
		"setOffByDeviceName"						| json([deviceName: dName]) 	    	| [req(endpointSetTemp, [hvac_mode: "off"], "setOffByDeviceName")]
		"setHeatModeByDeviceId"						| json([deviceId: dId])     			| [req(endpointSetTemp, [hvac_mode: "heat"], "setHeatModeByDeviceId")]
		"setHeatModeByDeviceName"					| json([deviceName: dName])     		| [req(endpointSetTemp, [hvac_mode: "heat"], "setHeatModeByDeviceName")]
		"setCoolModeByDeviceId"						| json([deviceId: dId])     			| [req(endpointSetTemp, [hvac_mode: "cool"], "setCoolModeByDeviceId")]
		"setCoolModeByDeviceName"					| json([deviceName: dName])     		| [req(endpointSetTemp, [hvac_mode: "cool"], "setCoolModeByDeviceName")]
		"setHeatCoolModeByDeviceId"					| json([deviceId: dId])    				| [req(endpointSetTemp, [hvac_mode: "heat-cool"], "setHeatCoolModeByDeviceId")]
		"setHeatCoolModeByDeviceName"				| json([deviceName: dName])     		| [req(endpointSetTemp, [hvac_mode: "heat-cool"], "setHeatCoolModeByDeviceName")]
	}

	@Unroll
	def "valid onAction #actionName for sub-device"(actionName, actionParams, expectedReqList) {
		expect:
		def ts = 42L
		def action = new ActionDef(Option.apply("sdid"), "ddid", Option.apply('peyiJNo0IldT2YlIVtYaGQ'), ts, actionName, actionParams)
		def expected = new ActionResponse([new ActionRequest(expectedReqList)])
		def result = sut.onAction(ctx, action, info)

		result.isGood()
		result.get() == expected

		where:
		actionName									| actionParams							| expectedReqList
		"getAllData"								| ""									| [new RequestDef(endpoint).withHeaders(["X-Artik-Action": "getAllData"])]
		"setTemperature"							| json([temp: 20])						| [req(endpointSetTemp, [target_temperature_c: 20], "setTemperature")]
		"setTemperatureInFahrenheit"				| json([temp: 50])						| [req(endpointSetTemp, [target_temperature_f: 50], "setTemperatureInFahrenheit")]
		"setOff"									| json([:])								| [req(endpointSetTemp, [hvac_mode: "off"], "setOff")]
		"setHeatMode"								| json([:])     						| [req(endpointSetTemp, [hvac_mode: "heat"], "setHeatMode")]
		"setCoolMode"								| json([:])     						| [req(endpointSetTemp, [hvac_mode: "cool"], "setCoolMode")]
		"setHeatCoolMode"							| json([:])    							| [req(endpointSetTemp, [hvac_mode: "heat-cool"], "setHeatCoolMode")]
	}

	@Unroll
	def "valid onFetchResponse #request "(request, expectedEvents) {
		expect:
		def msg = readFile(this, "thermostatExample.json")
		def fetchedResponse = new Response(HttpURLConnection.HTTP_OK, "application/json", msg)
		def result = sut.onFetchResponse(ctx, request, info, fetchedResponse)
		def expected = expectedEvents.collect{ event -> new Event(10, json(event))}

		result.isGood()
		result.get() == expected

		where:
		request																			| expectedEvents
		req(endpointSetTemp, [hvac_mode: "off"], "setOffByDeviceName")					| [ [device_id: dId, hvac_mode: "off"] ]
		req(endpointSetTemp, [hvac_mode: "heat"], "setHeatModeByDeviceName")			| [ [device_id: dId, hvac_mode: "heat"] ]
		req(endpointSetTemp, [hvac_mode: "heat-cool"], "setHeatCoolModeByDeviceName")	| [ [device_id: dId, hvac_mode: "heat-cool"] ]
		req(endpointSetHomes, [away: "away"], "setAwayByStructureName")					| [ ["structure": [ away: "away", structure_id: sId1] ]]
	}

	def "on fetch Response creation and delete Event after Action synchronizeDevices"() {
		when:
		def testCtx = new FakeContext() {
			Map parameters() {[
				"productType":"thermostat",
				"endpoint": endpoint
			]}
			List<String> findExtSubDeviceId(DeviceInfo dInfo) {
		        ["did0","did1"]
		    }
		}
		def givenReq = new RequestDef(endpoint).withHeaders(['X-Artik-Action': 'synchronizeDevices'])
		def msg = readFile(this, "testDevices.json")
		def response = new Response(HttpURLConnection.HTTP_OK, "application/json", msg)
		def expectedEvents = [
			new Event(10, '''{"devices":{"dname1":["did1"],"dname2":["did2"]},"devicesLowerCase":{"dname1":["did1"],"dname2":["did2"]},"structures":{"sName":["sid0","sid1"]},"structuresLowerCase":{"sname":["sid0","sid1"]}}''', EventType.user),
			new Event(10, 'dname1', EventType.createOrUpdateDevice, Option.apply('did1')),
			new Event(10, 'dname2', EventType.createOrUpdateDevice, Option.apply('did2')),
			new Event(10, '', EventType.deleteDevice, Option.apply('did0'))
		]
		def res = sut.onFetchResponse(testCtx, givenReq, info, response)

		then:
		res.isGood()
		res.get().size() == expectedEvents.size()
		res.get() == expectedEvents
	}

	def "on fetch Response creation and delete Event after Action synchronizeDevices with prefix/suffix"() {
		when:
		def testCtx = new FakeContext() {
			Map parameters() {[
				"productType":"thermostat",
				"endpoint": endpoint,
				"prefix": "prefix ",
				"suffix": " suffix"
			]}
			List<String> findExtSubDeviceId(DeviceInfo dInfo) {
		        ["did0","did1"]
		    }
		}
		def givenReq = new RequestDef(endpoint).withHeaders(['X-Artik-Action': 'synchronizeDevices'])
		def msg = readFile(this, "testDevices.json")
		def response = new Response(HttpURLConnection.HTTP_OK, "application/json", msg)
		def expectedEvents = [
			new Event(10, '''{"devices":{"dname1":["did1"],"dname2":["did2"]},"devicesLowerCase":{"dname1":["did1"],"dname2":["did2"]},"structures":{"sName":["sid0","sid1"]},"structuresLowerCase":{"sname":["sid0","sid1"]}}''', EventType.user),
			new Event(10, 'prefix dname1 suffix', EventType.createOrUpdateDevice, Option.apply('did1')),
			new Event(10, 'prefix dname2 suffix', EventType.createOrUpdateDevice, Option.apply('did2')),
			new Event(10, '', EventType.deleteDevice, Option.apply('did0'))
		]
		def res = sut.onFetchResponse(testCtx, givenReq, info, response)

		then:
		res.isGood()
		res.get().size() == expectedEvents.size()
		res.get() == expectedEvents
	}
}