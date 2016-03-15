package io.samsungsami.openWeatherMap

import com.samsung.sami.cloudconnector.api_v1.*
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter
import org.scalactic.Bad
import org.scalactic.Good
import org.scalactic.Or
import scala.math.BigDecimal

import java.math.MathContext
import java.math.RoundingMode

import static java.net.HttpURLConnection.HTTP_OK

class MyCloudConnector extends CloudConnector {
    static final String currentWeatherUrl = "http://api.openweathermap.org/data/2.5/weather"
    static final String forecastWeatherUrl = "http://api.openweathermap.org/data/2.5/forecast"
    static final DateTimeFormatter ymdDateFormat = DateTimeFormat.forPattern("yyyy-MM-dd");
    static final DateTimeFormatter ymdhmsDateFormat = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
    def JsonSlurper slurper = new JsonSlurper()
    static final timePeriods = ["evening", "afternoon", "morning", "night"]
    static final allowedKeys = timePeriods + [
            "all",
            "beaufort",
            "clouds", "coord", "country",
            "date", "deg", "dt",
            "humidity",
            "id", "icon", "icon_later",
            "lat", "long",
            "main", "main_later",
            "name",
            "period", "pressure",
            "rain",
            "sys", "sunrise", "sunset", "speed", "snow",
            "text",
            "temp", "temp_min", "temp_max", "three_hours", "type",
            "wind", "weather"
    ]


    def Or<RequestDef, Failure> signAndPrepare(Context ctx, RequestDef req, DeviceInfo info, Phase phase) {
        def appId = ctx.parameters().get("apiKey")
        switch (phase) {
            case Phase.subscribe:
            case Phase.unsubscribe:
            case Phase.undef:
            case Phase.fetch:
                return new Good(req.addQueryParams(["appid": appId]))
            case Phase.refreshToken:
            default:
                super.signAndPrepare(ctx, req, info, phase)
        }
    }

    @Override
    def Or<ActionResponse, Failure> onAction(Context ctx, ActionDef action, DeviceInfo dInfo) {
        def dSelector = new BySamiDeviceId(dInfo.did)
        def json = slurper.parseText(action.params)
        switch (action.name) {
            case "getCurrentWeatherByCity":
                RequestDef req = new RequestDef(currentWeatherUrl).withQueryParams(["units": "metric"])
                if (json.city == null) {
                    return new Bad(new Failure("unsupported action for openWeatherMap getData without city " + action.params))
                }
                String city = json.city
                if (json.countryCode != null) {
                    city = city + "," + json.countryCode
                }
                req = req.addQueryParams(["q": city])
                return new Good(new ActionResponse([new ActionRequest([req])]))
            case "getCurrentWeatherByGPSLocation":
                RequestDef req = new RequestDef(currentWeatherUrl).withQueryParams(["units": "metric"])
                def keyAndParams = [["lat", json?.lat],
                                    ["lon", json?.long]]
                return buildActionResponse(req, dSelector, keyAndParams)
            case "getForecastWeatherByGPSLocation":
                RequestDef req = new RequestDef(forecastWeatherUrl).withQueryParams(["units": "metric"])
                def keyAndParams = [["lat", json?.lat],
                                    ["lon", json?.long],
                                    ["daysToForecast", json?.daysToForecast]]
                return buildActionResponse(req, dSelector, keyAndParams)
            case "getWeatherSummary":
                def tz = null
                try {
                    tz = DateTimeZone.forID(json.timeZone ?: "UTC").toString()
                } catch (IllegalArgumentException e) {
                    return new Bad(new Failure("invalid action parameter 'timeZone' of action getWeather of openWeatherMap:" + json.timeZone))
                }
                json += ["city": json?.in, "mustSummary": "true", "TimeZone": tz]
                switch (json.for) {
                    case "today": json += ["daysToForecast": "0"]; break
                    case "tomorrow": json += ["daysToForecast": "1"]; break
                    default: return new Bad(new Failure("missing or invalid action parameter 'for' of action getWeather of openWeatherMap:"))
                }
                //no break instruction : intentionally following with next case's code
            case "getForecastWeatherByCity":
                RequestDef req = new RequestDef(forecastWeatherUrl).withQueryParams(["units": "metric"])
                if (json.city == null) {
                    return new Bad(new Failure("unsupported action for openWeatherMap getData without city " + action.params))
                }
                if (json.daysToForecast == null) {
                    return new Bad(new Failure("unsupported action for openWeatherMap getData without daysToForecast " + action.params))
                }
                String city = json.city
                if (json.countryCode != null) {
                    city = city + "," + json.countryCode
                }
                req = req.addQueryParams(["q": city, "daysToForecast": (json?.daysToForecast), "mustSummary": (json?.mustSummary ?: false)])
                return new Good(new ActionResponse([new ActionRequest([req])]))
            default:
                break
        }
        return new Bad(new Failure("unsupported action for openWeatherMap:" + action.name))
    }


    @Override
    def Or<List<Event>, Failure> onFetchResponse(Context ctx, RequestDef req, DeviceInfo info, Response res) {
        DateTime now = new DateTime(DateTimeZone.UTC);
        def commonJson = [:]


        switch (res.status) {
            case HTTP_OK:
                switch (req.url) {
                    case currentWeatherUrl:
                        def date = ymdDateFormat.print(now)
                        commonJson += ["type": "current", "date": date]
                        def json = slurper.parseText(res.content())
                        return new Good(eventsForOneTimeFrame(json, now.millis, commonJson))
                    case forecastWeatherUrl:
                        def json = slurper.parseText(res.content())
                        def daysToForecast = req.queryParams().get("daysToForecast").toInteger()
                        def mustSummary = req.queryParams().get("mustSummary")?.toBoolean() ?: false
                        def filter = ymdDateFormat.print(now.plusDays(daysToForecast))
                        def list = json?.list?.findAll({ it?.dt_txt?.startsWith(filter) } ?: false) ?: []
                        def events = null
                        if (mustSummary) {
                            commonJson += weatherJsonTransFormation(json?.city) + ["type": "summary"]
                            events = weatherSummary(list, now.millis, commonJson)
                        } else {
                            commonJson += weatherJsonTransFormation(json?.city) + ["type": "forecast"]
                            events = list.collectMany { prediction -> eventsForOneTimeFrame(prediction, now.millis, commonJson) }
                        }
                        return new Good(events)
                    default:
                        return new Bad(new Failure("receiving Responsse ${res} fron unknown request ${req}"))
                }
                break
            default:
                return new Bad(new Failure("[${info.did}] onFetchResponse got status http status : ${res.status()}) with content: ${res.content()}"))
        }
    }


    private def weatherJsonTransFormation(json) {
        transformJson(json, { k, v ->
            if (k == "lon")
                ["long": (v)]
            else if (k == "description")
                ["text": (v)]
            else if (k == "dt_txt")
                ["date": (v)]
            else if (k == "3h")
                ["three_hours": (v)]
            else if (k != "weather")
                [(k): (v)]
            else
                [:]
        })
    }

    private def eventsForOneTimeFrame(json, ts, addToEvent) {
        def msgWithoutWeather = weatherJsonTransFormation(json)
        def events = json?.weather.collect { weatherData ->
            def msg = msgWithoutWeather + ["weather": weatherJsonTransFormation(weatherData)] + addToEvent
            new Event(ts, outputJson(msg))
        }
        return events
    }

    private def windStrength(java.math.BigDecimal windSpeed) {
        if (windSpeed < 0.3)
            return 0
        if (windSpeed <= 1.5)
            return 1
        if (windSpeed <= 3.3)
            return 2
        if (windSpeed <= 5.5)
            return 3
        if (windSpeed <= 7.9)
            return 4
        if (windSpeed <= 10.7)
            return 5
        if (windSpeed <= 13.8)
            return 6
        if (windSpeed <= 17.1)
            return 7
        if (windSpeed <= 20.7)
            return 8
        if (windSpeed <= 24.4)
            return 9
        if (windSpeed <= 28.4)
            return 10
        if (windSpeed <= 32.6)
            return 11
        return 12
    }

    private def windDescription(Integer windStrength) {
        switch (windStrength) {
            case 0:
            case 1: return "calm"
            case 2:
            case 3: return "breezy"
            case 4:
            case 5: return "windy"
            case 6:
            case 7: return "very windy"
            case 8:
            default: return "stormy"
        }
    }

    def weatherSummary(list, ts, addToEvent) {
        def parser = 0
        list = list.reverse()
        def summaries = timePeriods.collectMany { period ->
            if (parser + 1 < list.size()) {
                def description = list[parser].weather[0].description
                def icon = list[parser].weather[0].icon
                def main = list[parser].weather[0].main
                def temp_min = new BigDecimal(list[parser].main.temp_min).min(new BigDecimal(list[parser + 1].main.temp_min)).round(new MathContext(3, RoundingMode.HALF_DOWN))
                def temp_max = new BigDecimal(list[parser].main.temp_max).max(new BigDecimal(list[parser + 1].main.temp_max)).round(new MathContext(3, RoundingMode.HALF_DOWN))
                def windSpeed = (list[parser].wind.speed + list[parser + 1].wind.speed) / 2
                def windStrength = windStrength(windSpeed)

                def description2 = list[parser + 1].weather[0].description
                def msg = ["temp_min": temp_min,
                           "temp_max": temp_max,
                           "icon"    : icon,
                           "main"    : main,
                           "wind": ["text": windDescription(windStrength),
                                    "speed"   : windSpeed,
                                    "beaufort": windStrength]
                ]
                if (description == description2) {
                    msg = msg + ["text": description + "."]
                } else {
                    def icon2 = list[parser + 1].weather[0].icon
                    def main2 = list[parser + 1].weather[0].main
                    parser += 2
                    msg = msg + ["text": description + " followed by " + description2 + ".",
                                 "icon_later" : icon2,
                                 "main_later" : main2
                    ]
                }
                [msg]
            } else if (parser + 1 == list.size()) {
                def description = list[parser].weather[0].description
                def icon = list[parser].weather[0].icon
                def main = list[parser].weather[0].main
                def temp_min = new BigDecimal(list[parser].main.temp_min).round(new MathContext(3, RoundingMode.HALF_DOWN))
                def temp_max = new BigDecimal(list[parser].main.temp_max).round(new MathContext(3, RoundingMode.HALF_DOWN))
                def windSpeed = list[parser].wind.speed
                def windStrength = windStrength(windSpeed)

                def msg = ["temp_min": temp_min,
                           "temp_max": temp_max,
                           "icon"    : icon,
                           "main"    : main,
                           "text": description + ".",
                           "wind"    : ["text": windDescription(windStrength),
                                        "speed"   : windSpeed,
                                        "beaufort": windStrength]
                ]
                [msg]
            } else {
                []
            }
        }
        def events = addToEvent
        if (summaries.size() > 3) {
            events += [(timePeriods[3]): weatherJsonTransFormation(summaries[3])]
        }
        if (summaries.size() > 2) {
            events += [(timePeriods[2]): weatherJsonTransFormation(summaries[2])]
        }
        if (summaries.size() > 1) {
            events += [(timePeriods[1]): weatherJsonTransFormation(summaries[1])]
        }
        if (summaries.size() > 0) {
            events += [(timePeriods[0]): weatherJsonTransFormation(summaries[0])]
        }
        return [new Event(ts, outputJson(events))]
    }

    /*
    HELPERS
     */

    private def outputJson(json) {
        JsonOutput.toJson(
                transformJson(json, { k, v ->
                    if (allowedKeys.contains(k))
                        [(k): (v)]
                    else
                        [:]
                })
        ).trim()
    }

    private def buildActionResponse(RequestDef req, DeviceSelector dSelector, List<List<String>> keyAndParams) {
        keyAndParams.collect { keyAndParam ->
            def param = keyAndParam[1]
            if (param == null) {
                return new Bad(new Failure("unsupported action for openWeatherMap getData without " + keyAndParam[1]))
            }
            def key = keyAndParam[0]
            req = req.addQueryParams([(key): param.toString()])
        }
        new Good(new ActionResponse([new ActionRequest([req])]))
    }

    private def transformJson(obj, f) {
        if (obj instanceof java.util.Map) {
            obj.collectEntries { k, v ->
                if (v != null) {
                    def newV = transformJson(v, f)
                    if (newV != [:]) {
                        f(k, newV)
                    } else {
                        [:]
                    }
                } else {
                    [:]
                }
            }
        } else if (obj instanceof java.util.Collection) {
            java.util.Collection newList = obj.collect { item ->
                transformJson(item, f)
            }
            (newList.isEmpty()) ? [:] : newList
        } else {
            obj
        }
    }
}