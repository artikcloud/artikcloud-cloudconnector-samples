package io.samsungsami.netatmo

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
			[:]
		}
	}
	def extId = "23138311640030064"
	def apiEndpoint = "https://api.netatmo.com/api/"
	def stationEndpoint = "${apiEndpoint}getstationsdata"
	def info = new DeviceInfo("deviceId", Option.apply(extId), new Credentials(AuthType.OAuth2, "", "1j0v33o6c5b34cVPqIiB_M2LYb_iM5S9Vcy7Rx7jA2630pK7HIjEXvJoiE8V5rRF", Empty.option(), Option.apply("bearer"), ctx.scope(), Empty.option()), ctx.cloudId(), Empty.option())

	def "create events from fetch response"() {
		when:
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
}
