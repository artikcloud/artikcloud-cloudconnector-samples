package io.samsungsami.netatmo

import com.samsung.sami.cloudconnector.api_v1.*
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
		def res = sut.onFetchResponse(ctx, req, info , fetchedResponse)
		def ts = 1441872018
		def expectedEvents = [
				new Event(ts, '''{"netatmoId": "70:ee:50:00:00:14", "module": {"moduleType":"NAModule3" ,"dataType":"Rain" ,"battery":4103,"gustStrength":null,"gustAngle":null,"windStrength":null,"windAngle":null,"rain":0.101,"rain1h":null,"rain24h":null}}'''),
				new Event(ts, '''{"netatmoId": "70:ee:50:00:00:14", "module": {"moduleType":"NAModule4" ,"dataType":"Temperature, CO2, Humidity" ,"battery":44568,"gustStrength":null,"gustAngle":null,"windStrength":null,"windAngle":null,"rain":null,"rain1h":null,"rain24h":null}}'''),
				new Event(ts, '''{"netatmoId": "70:ee:50:00:00:14", "module": {"moduleType":"NAModule1" ,"dataType":"Temperature, Humidity" ,"battery":31188,"gustStrength":null,"gustAngle":null,"windStrength":null,"windAngle":null,"rain":null,"rain1h":null,"rain24h":null}}'''),
				new Event(ts, '''{"netatmoId": "70:ee:50:00:00:14", "module": {"moduleType":"NAModule2" ,"dataType":"Wind" ,"battery":31188,"gustStrength":35,"gustAngle":156,"windStrength":25,"windAngle":155,"rain":null,"rain1h":null,"rain24h":null}}'''),
				new Event(ts, '''{"netatmoId": "70:ee:50:00:00:14", "station": {"city":"Saint-Denis" ,"altitude":30.478512648583,"lat":2.384033203125,"long":48.936934954094,"timeZone":"Europe/Paris" ,"wifiStatus":109,"stationType":"NAMain" ,"temp":3.2,"humidity":60,"co2":4852,"noise":110,"pressure":929.4,"absolutePressure":929.4,"pressureTrend":null,"tempTrend":null,"maxTemp":79.5,"minTemp":-39.5,"dateMaxTemp":1441850941,"dateMinTemp":1441862941}}''')
						]
//{"module": {"moduleType":"NAModule3" ,"dataType":"Rain" ,"battery":4103,"gustStrength":null,"gustAngle":null,"windStrength":null,"windAngle":null,"rain":0.101,"rain1h":null,"rain24h":null}}
		then:
		res.isGood()
		res.get()[0] == expectedEvents[0]
		res.get()[1] == expectedEvents[1]
		res.get()[2] == expectedEvents[2]
		res.get()[3] == expectedEvents[3]
		res.get().size() == expectedEvents.size()
		res.get() == expectedEvents
	}
}
