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

	def "on action fetch send request for current weather"() {
		when:
		def req = new RequestDef(currentWeatherUrl).withQueryParams(["units":"metric"])
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
				new ActionRequest([req.addQueryParams(["q":"cervieres,fr"])]),
				new ActionRequest([req.addQueryParams(["q":"cervieres"])]),
				new ActionRequest([req.addQueryParams(["lat":"42", "lon":"42"])]),
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
		def ts = 1456914668
		def expectedEvents = [
				new Event(ts, '''{"base":"cmc stations","clouds":{"all":8},"cod":200,"coord":{"lat":44.9,"long":6.65},"dt":1456914668,"id":3030142,"main":{"grnd_level":820.48,"humidity":89,"pressure":820.48,"sea_level":1023.01,"temp":277.85,"temp_max":277.85,"temp_min":277.85},"name":"Briancon","rain":{"three_hours":0},"sys":{"country":"FR","message":0.0028,"sunrise":1456898888,"sunset":1456939396},"wind":{"deg":254.009,"speed":1.01},"weather":{"description":"clear sky","icon":"02d","id":800,"main":"Clear"}}'''),
		]
		then:
		res.isGood()
		res.get()[0] == expectedEvents[0]
		res.get().size() == expectedEvents.size()
		res.get() == expectedEvents
	}
}