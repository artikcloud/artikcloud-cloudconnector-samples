package cloudconnector

import cloud.artik.cloudconnector.api_v1.*
import groovy.json.JsonSlurper
import org.scalactic.*
import scala.Option
import spock.lang.*
import utils.*

import java.text.SimpleDateFormat

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

	def JsonSlurper slurper = new JsonSlurper()

	static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
	Calendar cal = Calendar.getInstance()
	def date = dateFormat.format(cal.getTime())

	def "on action fetch send request for current weather"() {
		when:
		def req = new RequestDef(currentWeatherUrl).withQueryParams(["units":"metric"])
		def foreCastReq = new RequestDef(forecastWeatherUrl).withQueryParams(["units":"metric"])
		def actionByCity = "getCurrentWeatherByCity"
		def actionParam = '''{"countryCode":"fr","city":"cervieres"}'''
		def actionParamNoCountryCode = '''{"city":"cervieres"}'''
		def actionParamLatLong = '''{"lat":42,"long":42}'''
		def actionParamLatLongPrediction = '''{"lat":42,"long":42,"daysToForecast":1}'''
		def actionToday = '''{"in":"cervieres","for":"today","units":"imperial"}'''
		def actionTomorrow = '''{"countryCode":"fr","in":"cervieres","for":"tomorrow", "timeZone":"Europe/Paris"}'''
		def actionTodayNoTZ = '''{"in":"cervieres","for":"today", "timeZone":"babar the elephant"}'''
		def ts = 1441872018L
		def actionDef = new ActionDef(Option.apply("sdid"), "ddid", ts, actionByCity, actionParam)
		def actionDefNoCountryCode = new ActionDef(Option.apply("sdid"), "ddid", ts, actionByCity, actionParamNoCountryCode)
		def actionDefLatLong = new ActionDef(Option.apply("sdid"), "ddid", ts, "getCurrentWeatherByGPSLocation", actionParamLatLong)
		def actionDefPrediction = new ActionDef(Option.apply("sdid"), "ddid", ts, "getForecastWeatherByGPSLocation", actionParamLatLongPrediction)
		def actionDefToday = new ActionDef(Option.apply("sdid"), "ddid", ts, "getWeatherSummary", actionToday)
		def actionDefTomorrow = new ActionDef(Option.apply("sdid"), "ddid", ts, "getWeatherSummary", actionTomorrow)
		def actionDefTodayNoTZ = new ActionDef(Option.apply("sdid"), "ddid", ts, "getWeatherSummary", actionTodayNoTZ)
		def res = [sut.onAction(ctx, actionDef, info)] +
				[sut.onAction(ctx, actionDefNoCountryCode, info)] +
				[sut.onAction(ctx, actionDefLatLong, info)] +
				[sut.onAction(ctx, actionDefPrediction, info)] +
				[sut.onAction(ctx, actionDefToday, info)] +
				[sut.onAction(ctx, actionDefTomorrow, info)]
		def expectedEvents = [
				new ActionRequest([req.addQueryParams(["q":"cervieres,fr"])]),
				new ActionRequest([req.addQueryParams(["q":"cervieres"])]),
				new ActionRequest([req.addQueryParams(["lat":"42", "lon":"42"])]),
				new ActionRequest([foreCastReq.addQueryParams(["lat":"42", "lon":"42","daysToForecast":"1"])]),
				new ActionRequest([foreCastReq.addQueryParams(["daysToForecast":"0", "q":"cervieres", "mustSummary": "true"])]),
				new ActionRequest([foreCastReq.addQueryParams(["daysToForecast":"1", "q":"cervieres,fr", "mustSummary": "true"])]),
		].collect{ actionReq -> new Good( new ActionResponse([actionReq]))}

		then:
		res[0].isGood()
		normalize(res) == normalize(expectedEvents)
	}

	def "create events from fetch currentTime"() {
		when:
		def msg = readFile(this, "weather.json")
		def fetchedResponse = new Response(HttpURLConnection.HTTP_OK, "application/json", msg)
		def req = new RequestDef(currentWeatherUrl)
		def res = sut.onFetchResponse(ctx, req, info,  fetchedResponse)
		def expectedEvents = '''{"clouds":{"all":8},"coord":{"lat":44.9,"long":6.65},"dt":1456914668,"id":3030142,"main":{"humidity":89,"pressure":820.48,"temp":277.85,"temp_max":277.85,"temp_min":277.85},"name":"Briancon","rain":{"three_hours":0},"sys":{"country":"FR","sunrise":1456898888,"sunset":1456939396},"wind":{"deg":254.009,"speed":1.01},"weather":{"text":"clear sky","icon":"02d","id":800,"main":"Clear"},"type":"current","date":"''' + date + '''"}'''

		then:
		res.isGood()
		normalize(res.get()[0].payload()) == normalize(expectedEvents)
	}

	def "do not send empty object after fetch"() {
		when:
		def msg = readFile(this, "weatherHole.json")
		def fetchedResponse = new Response(HttpURLConnection.HTTP_OK, "application/json", msg)
		def req = new RequestDef(currentWeatherUrl)
		def res = sut.onFetchResponse(ctx, req, info,  fetchedResponse)
		def expectedEvents = '''{"clouds":{"all":8},"coord":{"lat":44.9,"long":6.65},"dt":1456914668,"main":{"humidity":75,"pressure":863.98,"temp":3.74,"temp_max":4.55,"temp_min":3.74},"name":"Briancon","rain":{"three_hours":0},"wind":{"deg":254.009,"speed":1.01},"weather":{"text":"clear sky","icon":"02d","id":800,"main":"Clear"},"type":"current","date":"''' + date + '''"}'''

		then:
		res.isGood()
		normalize(res.get()[0].payload()) == normalize(expectedEvents)
	}

	def "summary events"() {
		when:
		def json = slurper.parseText(readFile(this, "weatherSummary.json"))

		def ts = 1456914668000
		def res = sut.weatherSummary(json.list, ts, ["type": "summary"])
		def expectedEvents = [
				new Event(ts, '''{"type":"summary","night":{"temp_min":275,"temp_max":276,"icon":"10d","main":"Rain","wind":{"text":"breezy","speed":4.92,"beaufort":3},"text":"light rain."},"morning":{"temp_min":275,"temp_max":276,"icon":"10d","main":"Rain","wind":{"text":"breezy","speed":4.92,"beaufort":3},"text":"light rain."},"afternoon":{"temp_min":275,"temp_max":276,"icon":"10d","main":"Rain","wind":{"text":"breezy","speed":4.92,"beaufort":3},"text":"light rain."},"evening":{"temp_min":274,"temp_max":275,"icon":"10n","main":"Rain","wind":{"text":"breezy","speed":4.135,"beaufort":3},"text":"light rain followed by moderate rain.","icon_later":"10n","main_later":"Rain"}}'''),
		]
		then:
		cmpTasks(res, expectedEvents)
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
				new Event(ts, '''{"clouds":{"all":76},"dt":1457222400,"date":"2016-03-06 00:00:00","main":{"humidity":97,"pressure":1003.95,"temp":273.476,"temp_max":273.476,"temp_min":273.476},"snow":{"three_hours":0.0625},"wind":{"deg":233,"speed":3.56},"weather":{"text":"light snow","icon":"13n","id":600,"main":"Snow"},"coord":{"lat":55.75222,"long":37.615555},"country":"RU","id":524901,"name":"Moscow","type":"forecast"}'''),
				new Event(ts, '''{"clouds":{"all":92},"dt":1457146800,"date":"2016-03-05 03:00:00","main":{"humidity":98,"pressure":994.41,"temp":273.631,"temp_max":273.631,"temp_min":273.631},"rain":{"three_hours":0.13},"snow":{"three_hours":1.2175},"wind":{"deg":91.5005,"speed":3.59},"weather":{"text":"light rain","icon":"10n","id":500,"main":"Rain"},"coord":{"lat":55.75222,"long":37.615555},"country":"RU","id":524901,"name":"Moscow","type":"forecast"}'''),
				new Event(ts, '''{"clouds":{"all":92},"dt":1457157600,"date":"2016-03-05 06:00:00","main":{"humidity":97,"pressure":994.38,"temp":273.768,"temp_max":273.768,"temp_min":273.768},"rain":{"three_hours":0.45},"snow":{"three_hours":0.075},"wind":{"deg":87.5021,"speed":2.6},"weather":{"text":"light rain","icon":"10d","id":500,"main":"Rain"},"coord":{"lat":55.75222,"long":37.615555},"country":"RU","id":524901,"name":"Moscow","type":"forecast"}''')
		]
		def expectedEvents1 = [
				new Event(ts, '''{"clouds":{"all":92},"dt":1457136000,"date":"2016-03-05 00:00:00","main":{"grnd_level":995.19,"humidity":100,"pressure":995.19,"sea_level":1015.48,"temp":273.66,"temp_kf":0,"temp_max":273.66,"temp_min":273.66},"rain":{},"snow":{"three_hours":2.885},"sys":{"population":0},"wind":{"deg":90.5001,"speed":4.13},"weather":{"text":"snow","icon":"13n","id":601,"main":"Snow"},"coord":{"lat":55.75222,"long":37.615555},"country":"RU","id":524901,"name":"Moscow","population":0}'''),
				new Event(ts, '''{"clouds":{"all":92},"dt":1457136000,"date":"2016-03-05 00:00:00","main":{"grnd_level":995.19,"humidity":100,"pressure":995.19,"sea_level":1015.48,"temp":273.66,"temp_kf":0,"temp_max":273.66,"temp_min":273.66},"rain":{},"snow":{"three_hours":2.885},"sys":{"population":0},"wind":{"deg":90.5001,"speed":4.13},"weather":{"text":"snow","icon":"13n","id":601,"main":"Snow"},"coord":{"lat":55.75222,"long":37.615555},"country":"RU","id":524901,"name":"Moscow","population":0}'''),
				new Event(ts, '''{"clouds":{"all":92},"dt":1457136000,"date":"2016-03-05 00:00:00","main":{"grnd_level":995.19,"humidity":100,"pressure":995.19,"sea_level":1015.48,"temp":273.66,"temp_kf":0,"temp_max":273.66,"temp_min":273.66},"rain":{},"snow":{"three_hours":2.885},"sys":{"population":0},"wind":{"deg":90.5001,"speed":4.13},"weather":{"text":"snow","icon":"13n","id":601,"main":"Snow"},"coord":{"lat":55.75222,"long":37.615555},"country":"RU","id":524901,"name":"Moscow","population":0}'''),
				new Event(ts, '''{"clouds":{"all":92},"dt":1457136000,"date":"2016-03-05 00:00:00","main":{"grnd_level":995.19,"humidity":100,"pressure":995.19,"sea_level":1015.48,"temp":273.66,"temp_kf":0,"temp_max":273.66,"temp_min":273.66},"rain":{},"snow":{"three_hours":2.885},"sys":{"population":0},"wind":{"deg":90.5001,"speed":4.13},"weather":{"text":"snow","icon":"13n","id":601,"main":"Snow"},"coord":{"lat":55.75222,"long":37.615555},"country":"RU","id":524901,"name":"Moscow","population":0}'''),
				new Event(ts, '''{"clouds":{"all":92},"dt":1457136000,"date":"2016-03-05 00:00:00","main":{"grnd_level":995.19,"humidity":100,"pressure":995.19,"sea_level":1015.48,"temp":273.66,"temp_kf":0,"temp_max":273.66,"temp_min":273.66},"rain":{},"snow":{"three_hours":2.885},"sys":{"population":0},"wind":{"deg":90.5001,"speed":4.13},"weather":{"text":"snow","icon":"13n","id":601,"main":"Snow"},"coord":{"lat":55.75222,"long":37.615555},"country":"RU","id":524901,"name":"Moscow","population":0}'''),
				new Event(ts, '''{"clouds":{"all":92},"dt":1457136000,"date":"2016-03-05 00:00:00","main":{"grnd_level":995.19,"humidity":100,"pressure":995.19,"sea_level":1015.48,"temp":273.66,"temp_kf":0,"temp_max":273.66,"temp_min":273.66},"rain":{},"snow":{"three_hours":2.885},"sys":{"population":0},"wind":{"deg":90.5001,"speed":4.13},"weather":{"text":"snow","icon":"13n","id":601,"main":"Snow"},"coord":{"lat":55.75222,"long":37.615555},"country":"RU","id":524901,"name":"Moscow","population":0}'''),
				new Event(ts, '''{"clouds":{"all":92},"dt":1457136000,"date":"2016-03-05 00:00:00","main":{"grnd_level":995.19,"humidity":100,"pressure":995.19,"sea_level":1015.48,"temp":273.66,"temp_kf":0,"temp_max":273.66,"temp_min":273.66},"rain":{},"snow":{"three_hours":2.885},"sys":{"population":0},"wind":{"deg":90.5001,"speed":4.13},"weather":{"text":"snow","icon":"13n","id":601,"main":"Snow"},"coord":{"lat":55.75222,"long":37.615555},"country":"RU","id":524901,"name":"Moscow","population":0}'''),
				new Event(ts, '''{"clouds":{"all":92},"dt":1457136000,"date":"2016-03-05 00:00:00","main":{"grnd_level":995.19,"humidity":100,"pressure":995.19,"sea_level":1015.48,"temp":273.66,"temp_kf":0,"temp_max":273.66,"temp_min":273.66},"rain":{},"snow":{"three_hours":2.885},"sys":{"population":0},"wind":{"deg":90.5001,"speed":4.13},"weather":{"text":"snow","icon":"13n","id":601,"main":"Snow"},"coord":{"lat":55.75222,"long":37.615555},"country":"RU","id":524901,"name":"Moscow","population":0}''')
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
