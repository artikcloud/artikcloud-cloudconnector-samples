package io.samsungsami.openWeatherMap

import com.samsung.sami.cloudconnector.api_v1.*
import org.scalactic.*
import scala.Option
import spock.lang.*
import utils.*

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
	static final String forecastWeatherUrl = "http://api.openweathermap.org/data/2.5/forecast"
	def info = new DeviceInfo("deviceId", Option.apply(extId), new Credentials(AuthType.OAuth2, "", "1j0v33o6c5b34cVPqIiB_M2LYb_iM5S9Vcy7Rx7jA2630pK7HIjEXvJoiE8V5rRF", Empty.option(), Option.apply("bearer"), ctx.scope(), Empty.option()), ctx.cloudId(), Empty.option())

	def "on action fetch send request for current weather"() {
		when:
		def req = new RequestDef(currentWeatherUrl).withQueryParams(["units":"metric"])
		def foreCastReq = new RequestDef(forecastWeatherUrl).withQueryParams(["units":"metric"])
		def actionByCity = "getCurrentWeatherByCity"
		def actionParam = '''{"countryCode":"fr","city":"cervieres"}'''
		def actionParamNoCountryCode = '''{"city":"cervieres"}'''
		def actionParamLatLong = '''{"lat":42,"long":42}'''
		def actionParamLatLongPrediction = '''{"lat":42,"long":42,"daysToForecast":1}'''
		def ts = 1441872018L
		def actionDef = new ActionDef(Option.apply("sdid"), "ddid", ts, actionByCity, actionParam)
		def actionDefNoCountryCode = new ActionDef(Option.apply("sdid"), "ddid", ts, actionByCity, actionParamNoCountryCode)
		def actionDefLatLong = new ActionDef(Option.apply("sdid"), "ddid", ts, "getCurrentWeatherByGPSLocation", actionParamLatLong)
		def actionDefPrediction = new ActionDef(Option.apply("sdid"), "ddid", ts, "getForecastWeatherByGPSLocation", actionParamLatLongPrediction)
		def res = [sut.onAction(ctx, actionDef, info)] + [sut.onAction(ctx, actionDefNoCountryCode, info)] + [sut.onAction(ctx, actionDefLatLong, info)] + [sut.onAction(ctx, actionDefPrediction, info)]
		def expectedEvents = [
				new ActionRequest([req.addQueryParams(["q":"cervieres,fr"])]),
				new ActionRequest([req.addQueryParams(["q":"cervieres"])]),
				new ActionRequest([req.addQueryParams(["lat":"42", "lon":"42"])]),
				new ActionRequest([foreCastReq.addQueryParams(["lat":"42", "lon":"42","daysToForecast":"1"])]),
		].collect{ actionReq -> new Good( new ActionResponse([actionReq]))}
		then:
		res[0].isGood()
		res[0].get() == expectedEvents[0].get()
		res[1].get() == expectedEvents[1].get()
		res[2].get() == expectedEvents[2].get()
		res[3].get() == expectedEvents[3].get()
		res.size() == expectedEvents.size()
		res == expectedEvents
	}

	def "create events from fetch currentTime"() {
		when:
		def msg = readFile(this, "weather.json")
		def fetchedResponse = new Response(HttpURLConnection.HTTP_OK, "application/json", msg)
		def req = new RequestDef(currentWeatherUrl)
		def res = sut.onFetchResponse(ctx, req, info,  fetchedResponse)
		def ts = 1456914668000
		def expectedEvents = [
				new Event(ts, '''{"clouds":{"all":8},"coord":{"lat":44.9,"long":6.65},"dt":1456914668,"id":3030142,"main":{"humidity":89,"pressure":820.48,"temp":277.85,"temp_max":277.85,"temp_min":277.85},"name":"Briancon","rain":{"three_hours":0},"sys":{"country":"FR","sunrise":1456898888,"sunset":1456939396},"wind":{"deg":254.009,"speed":1.01},"weather":{"description":"clear sky","icon":"02d","id":800,"main":"Clear"},"date":"now"}'''),
		]
		then:
		res.isGood()
		res.get()[0] == expectedEvents[0]
		res.get().size() == expectedEvents.size()
		res.get() == expectedEvents
	}

	def "do not send empty object after fetch"() {
		when:
		def msg = readFile(this, "weatherHole.json")
		def fetchedResponse = new Response(HttpURLConnection.HTTP_OK, "application/json", msg)
		def req = new RequestDef(currentWeatherUrl)
		def res = sut.onFetchResponse(ctx, req, info,  fetchedResponse)
		def ts = 1456914668000
		def expectedEvents = [
				new Event(ts, '''{"clouds":{"all":8},"coord":{"lat":44.9,"long":6.65},"dt":1456914668,"main":{"humidity":75,"pressure":863.98,"temp":3.74,"temp_max":4.55,"temp_min":3.74},"name":"Briancon","rain":{"three_hours":0},"wind":{"deg":254.009,"speed":1.01},"weather":{"description":"clear sky","icon":"02d","id":800,"main":"Clear"},"date":"now"}'''),
		]
		then:
		res.isGood()
		res.get()[0] == expectedEvents[0]
		res.get().size() == expectedEvents.size()
		res.get() == expectedEvents
	}



//UPDATE timestamp day in prediciton.json and test to replay this test
/*
	def "create events from fetch predictions"() {
		when:
		def msg = readFile(this, "prediction.json")
		def fetchedResponse = new Response(HttpURLConnection.HTTP_OK, "application/json", msg)
		def req = new RequestDef(forecastWeatherUrl).withQueryParams("daysToForecast":"0")
		def req2 = new RequestDef(forecastWeatherUrl).withQueryParams("daysToForecast":"3")
		def res = [sut.onFetchResponse(ctx, req, info,  fetchedResponse)]//, sut.onFetchResponse(ctx, req2, info,  fetchedResponse)]
		def ts = 1456930800000
		def expectedEvents0 = [
				new Event(ts, '''{"clouds":{"all":92},"dt":1456930800,"date":"2016-03-02 15:00:00","main":{"grnd_level":996.01,"humidity":97,"pressure":996.01,"sea_level":1016.2,"temp":275.01,"temp_kf":-0.7,"temp_max":275.706,"temp_min":275.01},"rain":{"three_hours":1.065},"snow":{},"sys":{"population":0},"wind":{"deg":163,"speed":4.92},"weather":{"description":"light rain","icon":"10d","id":500,"main":"Rain"},"coord":{"lat":55.75222,"long":37.615555},"country":"RU","id":524901,"name":"Moscow","population":0}'''),
				new Event(ts, '''{"clouds":{"all":92},"dt":1456941600,"date":"2016-03-02 18:00:00","main":{"grnd_level":995.92,"humidity":98,"pressure":995.92,"sea_level":1016.2,"temp":274.37,"temp_kf":-0.66,"temp_max":275.028,"temp_min":274.37},"rain":{"three_hours":4.5675},"snow":{},"sys":{"population":0},"wind":{"deg":174.504,"speed":4.76},"weather":{"description":"moderate rain","icon":"10n","id":501,"main":"Rain"},"coord":{"lat":55.75222,"long":37.615555},"country":"RU","id":524901,"name":"Moscow","population":0}'''),
				new Event(ts, '''{"clouds":{"all":88},"dt":1456952400,"date":"2016-03-02 21:00:00","main":{"grnd_level":996.21,"humidity":100,"pressure":996.21,"sea_level":1016.54,"temp":273.72,"temp_kf":-0.62,"temp_max":274.345,"temp_min":273.72},"rain":{"three_hours":1.5825},"snow":{"three_hours":0.14},"sys":{"population":0},"wind":{"deg":215.003,"speed":3.51},"weather":{"description":"light rain","icon":"10n","id":500,"main":"Rain"},"coord":{"lat":55.75222,"long":37.615555},"country":"RU","id":524901,"name":"Moscow","population":0}''')
		]
		def expectedEvents1 = [
				new Event(ts, '''{"clouds":{"all":92},"dt":1457136000,"date":"2016-03-05 00:00:00","main":{"grnd_level":995.19,"humidity":100,"pressure":995.19,"sea_level":1015.48,"temp":273.66,"temp_kf":0,"temp_max":273.66,"temp_min":273.66},"rain":{},"snow":{"three_hours":2.885},"sys":{"population":0},"wind":{"deg":90.5001,"speed":4.13},"weather":{"description":"snow","icon":"13n","id":601,"main":"Snow"},"coord":{"lat":55.75222,"long":37.615555},"country":"RU","id":524901,"name":"Moscow","population":0}'''),
				new Event(ts, '''{"clouds":{"all":92},"dt":1457136000,"date":"2016-03-05 00:00:00","main":{"grnd_level":995.19,"humidity":100,"pressure":995.19,"sea_level":1015.48,"temp":273.66,"temp_kf":0,"temp_max":273.66,"temp_min":273.66},"rain":{},"snow":{"three_hours":2.885},"sys":{"population":0},"wind":{"deg":90.5001,"speed":4.13},"weather":{"description":"snow","icon":"13n","id":601,"main":"Snow"},"coord":{"lat":55.75222,"long":37.615555},"country":"RU","id":524901,"name":"Moscow","population":0}'''),
				new Event(ts, '''{"clouds":{"all":92},"dt":1457136000,"date":"2016-03-05 00:00:00","main":{"grnd_level":995.19,"humidity":100,"pressure":995.19,"sea_level":1015.48,"temp":273.66,"temp_kf":0,"temp_max":273.66,"temp_min":273.66},"rain":{},"snow":{"three_hours":2.885},"sys":{"population":0},"wind":{"deg":90.5001,"speed":4.13},"weather":{"description":"snow","icon":"13n","id":601,"main":"Snow"},"coord":{"lat":55.75222,"long":37.615555},"country":"RU","id":524901,"name":"Moscow","population":0}'''),
				new Event(ts, '''{"clouds":{"all":92},"dt":1457136000,"date":"2016-03-05 00:00:00","main":{"grnd_level":995.19,"humidity":100,"pressure":995.19,"sea_level":1015.48,"temp":273.66,"temp_kf":0,"temp_max":273.66,"temp_min":273.66},"rain":{},"snow":{"three_hours":2.885},"sys":{"population":0},"wind":{"deg":90.5001,"speed":4.13},"weather":{"description":"snow","icon":"13n","id":601,"main":"Snow"},"coord":{"lat":55.75222,"long":37.615555},"country":"RU","id":524901,"name":"Moscow","population":0}'''),
				new Event(ts, '''{"clouds":{"all":92},"dt":1457136000,"date":"2016-03-05 00:00:00","main":{"grnd_level":995.19,"humidity":100,"pressure":995.19,"sea_level":1015.48,"temp":273.66,"temp_kf":0,"temp_max":273.66,"temp_min":273.66},"rain":{},"snow":{"three_hours":2.885},"sys":{"population":0},"wind":{"deg":90.5001,"speed":4.13},"weather":{"description":"snow","icon":"13n","id":601,"main":"Snow"},"coord":{"lat":55.75222,"long":37.615555},"country":"RU","id":524901,"name":"Moscow","population":0}'''),
				new Event(ts, '''{"clouds":{"all":92},"dt":1457136000,"date":"2016-03-05 00:00:00","main":{"grnd_level":995.19,"humidity":100,"pressure":995.19,"sea_level":1015.48,"temp":273.66,"temp_kf":0,"temp_max":273.66,"temp_min":273.66},"rain":{},"snow":{"three_hours":2.885},"sys":{"population":0},"wind":{"deg":90.5001,"speed":4.13},"weather":{"description":"snow","icon":"13n","id":601,"main":"Snow"},"coord":{"lat":55.75222,"long":37.615555},"country":"RU","id":524901,"name":"Moscow","population":0}'''),
				new Event(ts, '''{"clouds":{"all":92},"dt":1457136000,"date":"2016-03-05 00:00:00","main":{"grnd_level":995.19,"humidity":100,"pressure":995.19,"sea_level":1015.48,"temp":273.66,"temp_kf":0,"temp_max":273.66,"temp_min":273.66},"rain":{},"snow":{"three_hours":2.885},"sys":{"population":0},"wind":{"deg":90.5001,"speed":4.13},"weather":{"description":"snow","icon":"13n","id":601,"main":"Snow"},"coord":{"lat":55.75222,"long":37.615555},"country":"RU","id":524901,"name":"Moscow","population":0}'''),
				new Event(ts, '''{"clouds":{"all":92},"dt":1457136000,"date":"2016-03-05 00:00:00","main":{"grnd_level":995.19,"humidity":100,"pressure":995.19,"sea_level":1015.48,"temp":273.66,"temp_kf":0,"temp_max":273.66,"temp_min":273.66},"rain":{},"snow":{"three_hours":2.885},"sys":{"population":0},"wind":{"deg":90.5001,"speed":4.13},"weather":{"description":"snow","icon":"13n","id":601,"main":"Snow"},"coord":{"lat":55.75222,"long":37.615555},"country":"RU","id":524901,"name":"Moscow","population":0}''')
		]
		then:
		res[0].isGood()
		res[0].get()[0] == expectedEvents0[0]
		res[0].get()[1] == expectedEvents0[1]
		res[0].get()[2] == expectedEvents0[2]
		res[0].get().size() == expectedEvents0.size()
		res[0].get() == expectedEvents0
		res[0].isGood()
		res[1].get()[0] == expectedEvents1[0]
		res[1].get().size() == expectedEvents1.size()
	}
*/
}