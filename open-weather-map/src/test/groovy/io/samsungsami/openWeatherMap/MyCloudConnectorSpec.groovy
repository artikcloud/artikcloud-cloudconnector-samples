package io.samsungsami.openWeatherMap

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
	def currentWeatherUrl = "http://api.openweathermap.org/data/2.5/weather"
	def info = new DeviceInfo("deviceId", Option.apply(extId), new Credentials(AuthType.OAuth2, "", "1j0v33o6c5b34cVPqIiB_M2LYb_iM5S9Vcy7Rx7jA2630pK7HIjEXvJoiE8V5rRF", Empty.option(), Option.apply("bearer"), ctx.scope(), Empty.option()), ctx.cloudId(), Empty.option())
	def dSelector = new BySamiDeviceId("deviceId")

	def "on action fetch send request for current weather"() {
		when:
		def req = new RequestDef(currentWeatherUrl)
		def actionByCity = "getCurrentWeatherByCity"
		def actionParam = '''{"countryCode":"fr","city":"cervieres"}'''
		def actionParamNoCountryCode = '''{"city":"cervieres"}'''
		def actionParamLatLong = '''{"lat":42,"long":42}'''
		def ts = 1441872018L
		def actionDef = new ActionDef(Option.apply("sdid"), "ddid", ts, actionByCity, actionParam)
		def actionDefNoCountryCode = new ActionDef(Option.apply("sdid"), "ddid", ts, actionByCity, actionParamNoCountryCode)
		def actionDefLatLong = new ActionDef(Option.apply("sdid"), "ddid", ts, "getCurrentWeatherByGPSLocation", actionParamLatLong)
		def res = [sut.onAction(ctx, actionDef, info)] + [sut.onAction(ctx, actionDefNoCountryCode, info)] + [sut.onAction(ctx, actionDefLatLong, info)]
		def expectedEvents = [
				new ActionRequest(dSelector,[req.withQueryParams(["q":"cervieres,fr"])]),
				new ActionRequest(dSelector,[req.withQueryParams(["q":"cervieres"])]),
				new ActionRequest(dSelector,[req.withQueryParams(["latitude":42, "longitude":42])]),
		].collect{ actionReq -> new Good( new ActionResponse([actionReq]))}
		then:
		res[0].isGood()
		res[0].get() == expectedEvents[0].get()
		res[1].get() == expectedEvents[1].get()
		res[2].get() == expectedEvents[2].get()
		res.size() == expectedEvents.size()
		res == expectedEvents
	}

	def "create events from fetch response"() {
		when:
		def msg = readFile(this, "weather.json")
		def fetchedResponse = new Response(HttpURLConnection.HTTP_OK, "application/json", msg)
		def req = new RequestDef(currentWeatherUrl)
		def res = sut.onFetchResponse(ctx, req, info,  fetchedResponse)
		def ts = 1369824698
		def expectedEvents = [
				new Event(ts, '''{"clouds":{"all":92},"cod":200,"coord":{"lat":35,"long":139},"dt":1369824698,"id":1851632,"main":{"humidity":89,"pressure":1013,"temp":289.5,"temp_max":292.04,"temp_min":287.04},"name":"Shuzenji","rain":{"3h":0},"sys":{"country":"JP","sunrise":1369769524,"sunset":1369821049},"wind":{"deg":187.002,"speed":7.31},"weather":{"description":"overcast clouds","icon":"04n","id":804,"main":"clouds"}}'''),
		]
		then:
		res.isGood()
		res.get()[0] == expectedEvents[0]
		res.get().size() == expectedEvents.size()
		res.get() == expectedEvents
	}
}
