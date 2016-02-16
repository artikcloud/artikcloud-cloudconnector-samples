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
						new Event(ts, '''{"city":Saint-Denis,"altitude":30.478512648583,"latitude":2.384033203125,"longitude":48.936934954094"timeZone":Europe/Paris,"wifiStatus":109,"temperature":3.2,"humidity":60,"co2":4852,"noise":110,"pressure":929.4,"absolutePressure":929.4,"maxTemperature":79.5,"minTemperature":-39.5,"dateMaxTemperature":1441850941,"dateMinTemperature":1441862941}''')
						]

		then:
		res.isGood()
		res.get()[0] == expectedEvents[0]
		res.get().size() == expectedEvents.size()
		res.get() == expectedEvents
	}
}
