package io.samsungsami.netatmo

import cloud.artik.cloudconnector.api_v1.*
import org.joda.time.*
import org.scalactic.*
import scala.Option
import spock.lang.*
import utils.*
import groovy.json.JsonSlurper

import static java.net.HttpURLConnection.*
import static utils.Tools.*

class MyCloudConnectorSpec extends Specification {

	def sut = new MyCloudConnector()
	def ctx = new FakeContext()
	def extId = "23138311640030064"
	def apiEndpoint = "https://api.netatmo.com/api/"
	def stationEndpoint = "${apiEndpoint}getstationsdata"
	def info = new DeviceInfo("deviceId", Option.apply(extId), new Credentials(AuthType.OAuth2, "", "1j0v33o6c5b34cVPqIiB_M2LYb_iM5S9Vcy7Rx7jA2630pK7HIjEXvJoiE8V5rRF", Empty.option(), Option.apply("bearer"), ctx.scope(), Empty.option()), ctx.cloudId(), Empty.option())

	def "create events from fetch response"() {
		when:
		def ctx = new FakeContext() {
			Map parameters() {
				["netatmoProduct": "station"]
			}
		}
		def msg = readFile(this, "station.json")
		def fetchedResponse = new Response(HttpURLConnection.HTTP_OK, "application/json", msg)
		def req = new RequestDef(stationEndpoint)
		def res = sut.onFetchResponse(ctx, req, info,  fetchedResponse)
		def ts = 1441872018000L
		def expectedEvents = [
				new Event(ts, '''{"netatmoId":"70:ee:50:00:00:14","station":{"_id":"70:ee:50:00:00:14","co2_calibrating":false,"dashboard_data":{"AbsolutePressure":929.4,"CO2":4852,"Humidity":60,"Noise":110,"Pressure":929.4,"temp":3.2,"dateMaxTemp":1441850941,"dateMinTemp":1441862941,"maxTemp":79.5,"minTemp":-39.5,"time_utc":1441872001},"dataType":"Temperature,CO2,Humidity,Noise,Pressure","firmware":91,"last_status_store":1441872001,"last_upgrade":1440507643,"module_name":"ind","place":{"altitude":30.478512648583,"city":"Saint-Denis","country":"FR","improveLocProposed":true,"lat":2.384033203125,"long":48.936934954094,"timezone":"Europe/Paris"},"station_name":"Station","stationType":"NAMain","wifi_status":109}}'''),
				new Event(ts, '''{"netatmoId":"70:ee:50:00:00:14","module":{"_id":"05:00:00:00:00:14","battery_vp":4103,"dashboard_data":{"Rain":0.101,"time_utc":1437990885},"dataType":"Rain","firmware":91,"last_message":1437990885,"last_seen":1437990885,"module_name":"Pluie","rf_status":40,"moduleType":"NAModule3"}}'''),
				new Event(ts, '''{"netatmoId":"70:ee:50:00:00:14","module":{"_id":"03:00:00:00:00:14","battery_vp":44568,"dashboard_data":{"Humidity":23,"Noise":10,"temp":40.2,"dateMaxTemp":1437991547,"dateMinTemp":1437991549,"maxTemp":46.9,"minTemp":-31.7,"time_utc":1437991550},"dataType":"Temperature,CO2,Humidity","firmware":91,"last_message":1437991542,"last_seen":1437991541,"module_name":"Inter","rf_status":72,"moduleType":"NAModule4"}}'''),
				new Event(ts, '''{"netatmoId":"70:ee:50:00:00:14","module":{"_id":"02:00:00:00:00:14","battery_vp":31188,"dashboard_data":{"Humidity":60,"temp":3.2,"dateMaxTemp":1441851001,"dateMinTemp":1441863001,"maxTemp":79.5,"minTemp":-39.5,"time_utc":1441872001},"dataType":"Temperature,Humidity","firmware":91,"last_message":1441872001,"last_seen":1441868401,"module_name":"out","rf_status":143,"moduleType":"NAModule1"}}'''),
				new Event(ts, '''{"netatmoId":"70:ee:50:00:00:14","module":{"_id":"06:00:00:00:00:14","battery_vp":31188,"dashboard_data":{"GustAngle":156,"GustStrength":35,"WindAngle":155,"WindHistoric":[{"WindAngle":155,"WindStrength":25,"time_utc":1441868401},{"WindAngle":155,"WindStrength":25,"time_utc":1441869001},{"WindAngle":155,"WindStrength":25,"time_utc":1441869601},{"WindAngle":155,"WindStrength":25,"time_utc":1441870201},{"WindAngle":155,"WindStrength":25,"time_utc":1441870801},{"WindAngle":155,"WindStrength":25,"time_utc":1441871402},{"WindAngle":155,"WindStrength":25,"time_utc":1441872001}],"WindStrength":25,"date_max_wind_str":1441836001,"max_wind_angle":156,"max_wind_str":35,"time_utc":1441872001},"dataType":"Wind","firmware":91,"last_message":1441872001,"last_seen":1441868401,"module_name":"wind","rf_status":143,"moduleType":"NAModule2"}}''')
		]
		then:
		res.isGood()
		res.get()[0] == expectedEvents[0]
		res.get()[1] == expectedEvents[1]
		res.get()[2] == expectedEvents[2]
		res.get()[3] == expectedEvents[3]
		res.get()[4] == expectedEvents[4]
		res.get().size() == expectedEvents.size()
		res.get() == expectedEvents
	}

	def "test function renameJsonWithMap()"() {
		when:
		def JsonSlurper slurper = new JsonSlurper()
		def device = slurper.parseText('{"_id":123456,"toto":"234567"}')
		def renameMap = ["_id":"deviceId"]
		def renamedDevice = sut.renameJsonWithMap(device,renameMap)
		def expectedResult = slurper.parseText('{"deviceId":123456,"toto":"234567"}')
		then:
		renamedDevice == expectedResult
	}

	def "create events from fetch response for Thermostat"() {
		when:
		def ctx = new FakeContext() {
			Map parameters() {
				["netatmoProduct": "thermostat"]
			}
		}
		def msg = readFile(this, "getthermostat.json")
		def fetchedResponse = new Response(HttpURLConnection.HTTP_OK, "application/json", msg)
		def req = new RequestDef("${apiEndpoint}getthermostatsdata")
		def res = sut.onFetchResponse(ctx, req, info, fetchedResponse)
		def ts = 1460126438000L
		def expectedEvents = [
			new Event(ts, '''{"deviceId":"70:ee:50:1a:62:14","station_name":"Maison I","type":"NAPlug","wifi_status":37}'''),
			new Event(ts, '''{"moduleId":"04:00:00:1a:53:4e","battery_percent":100,"battery_vp":4804,"measured":{"setpoint_temp":25,"temp":25.6,"time":1460125804},"module_name":"Thermostat 0","rf_status":42,"setpoint":{"setpoint_endtime":1460132077,"setpoint_mode":"manual","setpoint_temp":25},"type":"NATherm1"}'''),
			new Event(ts, '''{"moduleId":"04:00:00:1a:53:4f","battery_percent":100,"battery_vp":4804,"measured":{"setpoint_temp":25,"temp":25.6,"time":1460125804},"module_name":"Thermostat 1","rf_status":42,"setpoint":{"setpoint_endtime":1460132077,"setpoint_mode":"manual","setpoint_temp":25},"type":"NATherm1"}'''),
			new Event(ts, '''{"deviceId":"70:ee:50:1a:62:15","station_name":"Maison II","type":"NAPlug","wifi_status":37}'''),
			new Event(ts, '''{"moduleId":"04:00:00:1a:53:50","battery_percent":100,"battery_vp":4804,"measured":{"setpoint_temp":25,"temp":25.6,"time":1460125804},"module_name":"Thermostat 2","rf_status":42,"setpoint":{"setpoint_endtime":1460132077,"setpoint_mode":"manual","setpoint_temp":25},"type":"NATherm1"}'''),
			new Event(ts, '''{"moduleId":"04:00:00:1a:53:51","battery_percent":100,"battery_vp":4804,"measured":{"setpoint_temp":25,"temp":25.6,"time":1460125804},"module_name":"Thermostat 3","rf_status":42,"setpoint":{"setpoint_endtime":1460132077,"setpoint_mode":"manual","setpoint_temp":25},"type":"NATherm1"}'''),
			new Event(ts, '''{"moduleId":"04:00:00:1a:53:52","battery_percent":100,"battery_vp":4804,"measured":{"setpoint_temp":25,"temp":25.6,"time":1460125804},"module_name":"Thermostat 4","rf_status":42,"setpoint":{"setpoint_endtime":1460132077,"setpoint_mode":"manual","setpoint_temp":25},"type":"NATherm1"}''')
		]
		then:
		res.isGood()
		res.get()[0] == expectedEvents[0]
		res.get()[1] == expectedEvents[1]
		res.get()[2] == expectedEvents[2]
		res.get()[3] == expectedEvents[3]
		res.get()[4] == expectedEvents[4]
		res.get()[5] == expectedEvents[5]
		res.get()[6] == expectedEvents[6]
		res.get().size() == expectedEvents.size()
		res.get() == expectedEvents
	}

}
