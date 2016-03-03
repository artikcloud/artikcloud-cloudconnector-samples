package io.samsungsami.openWeatherMap

import com.samsung.sami.cloudconnector.api_v1.*
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.scalactic.Bad
import org.scalactic.Good
import org.scalactic.Or

import java.text.SimpleDateFormat

import static java.net.HttpURLConnection.HTTP_OK

class MyCloudConnector extends CloudConnector {
    static final String currentWeatherUrl = "http://api.openweathermap.org/data/2.5/weather"
    static final String forecastWeatherUrl = "http://api.openweathermap.org/data/2.5/forecast"
    static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    def JsonSlurper slurper = new JsonSlurper()
    static final allowedKeys = [
            "all",
            "clouds","coord","country",
            "date","deg","description","dt",
            "humidity",
            "id","icon",
            "lat","long",
            "main",
            "name",
            "pressure",
            "rain",
            "sys","sunrise","sunset","speed","snow",
            "temp","temp_min","temp_max","three_hours",
            "wind","weather"
    ]



    def Or<RequestDef, Failure> signAndPrepare(Context ctx, RequestDef req, DeviceInfo info, Phase phase){
        def appId = ctx.parameters().get("apiKey")
        switch (phase) {
            case Phase.subscribe:
            case Phase.unsubscribe:
            case Phase.undef:
            case Phase.fetch:
                return new Good(req.addQueryParams(["appid":appId]))
                break
            case Phase.refreshToken:
            default:
                super.signAndPrepare(ctx, req, info, phase)
        }
    }

    @Override
    def  Or<ActionResponse, Failure> onAction(Context ctx, ActionDef action, DeviceInfo dInfo) {
        def dSelector = new BySamiDeviceId(dInfo.did)
        def json = slurper.parseText(action.params)
        switch (action.name) {
            case "getCurrentWeatherByCity":
                RequestDef req = new RequestDef(currentWeatherUrl).withQueryParams(["units":"metric"])
                if (json.city == null) {
                    return new Bad(new Failure("unsupported action for openWeatherMap getData without city " + action.params))
                }
                String city = json.city
                if (json.countryCode != null){
                    city = city + "," + json.countryCode
                }
                req = req.addQueryParams(["q":city])
                return new Good(new ActionResponse([new ActionRequest([req])]))
            case "getCurrentWeatherByGPSLocation":
                RequestDef req = new RequestDef(currentWeatherUrl).withQueryParams(["units":"metric"])
                def keyAndParams = [["lat", json?.lat],
                                    ["lon",json?.long]]
                return buildActionResponse(req, dSelector, keyAndParams)
            case "getForecastWeatherByCity":
                RequestDef req = new RequestDef(forecastWeatherUrl).withQueryParams(["units":"metric"])
                if (json.city == null) {
                    return new Bad(new Failure("unsupported action for openWeatherMap getData without city " + action.params))
                }
                String city = json.city
                if (json.countryCode != null){
                    city = city + "," + json.countryCode
                }
                if (json.daysToForecast == null) {
                    return new Bad(new Failure("unsupported action for openWeatherMap getData without daysToForecast " + action.params))
                }
                req = req.addQueryParams(["q":city, "daysToForecast": (json?.daysToForecast)])
                return new Good(new ActionResponse([new ActionRequest([req])]))
            case "getForecastWeatherByGPSLocation":
                RequestDef req = new RequestDef(forecastWeatherUrl).withQueryParams(["units":"metric"])
                def keyAndParams = [["lat", json?.lat],
                                    ["lon", json?.long],
                                    ["daysToForecast", json?.daysToForecast]]
                return buildActionResponse(req, dSelector, keyAndParams)
            default:
                break
        }
        return new Bad(new Failure("unsupported action for openWeatherMap:" + action.name))
    }


    @Override
    def Or<List<Event>, Failure> onFetchResponse(Context ctx, RequestDef req, DeviceInfo info, Response res) {
        def jsonNow = ["date":"now"]
        switch(res.status) {
            case HTTP_OK:
                switch(req.url) {
                    case currentWeatherUrl:
                        def json = slurper.parseText(res.content().trim())
                        return new Good(eventsForOneTimeFrame(json, json.dt  * 1000L, jsonNow))
                    case forecastWeatherUrl:
                        def json = slurper.parseText(res.content().trim())
                        def daysToForecast = req.queryParams().get("daysToForecast").toInteger()
                        Calendar cal = Calendar.getInstance()
                        cal.add(Calendar.DATE,daysToForecast)
                        def filter = dateFormat.format(cal.getTime())
                        def city = weatherJsonTransFormation(json?.city)
                       def ts = json?.list[0].dt * 1000L
                        def events = json?.list.collectMany { prediction ->
                            if (prediction?.dt_txt?.startsWith(filter) ?: false) {
                                eventsForOneTimeFrame(prediction, ts, city)
                            }
                            else {
                                []
                            }
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


    private def weatherJsonTransFormation(json){
        transformJson(json, {k,v ->
            if (k == "lon")
                ["long":(v)]
            else if (k == "dt_txt")
                ["date":(v)]
            else if (k == "3h")
                ["three_hours":(v)]
            else if (k != "weather")
                [(k): (v)]
            else
                [:]
        })
    }
    private def eventsForOneTimeFrame(json, ts, addToEvent) {
        def msgWithoutWeather = weatherJsonTransFormation(json)
        def events = json?.weather.collect{ weatherData ->
            def msg = msgWithoutWeather + ["weather": weatherData] + addToEvent
            new Event(ts, outputJson(msg))
        }
        return events
    }



    /*
    HELPERS
     */


    private def outputJson(json){
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
        keyAndParams.collect{ keyAndParam ->
            def param = keyAndParam[1]
            if (param == null) {
                return new Bad(new Failure("unsupported action for openWeatherMap getData without " + keyAndParam[1]))
            }
            def key = keyAndParam[0]
            req = req.addQueryParams([(key):param.toString()])
        }
        new Good(new ActionResponse([new ActionRequest([req])]))
    }

    private def transformJson(obj, f) {
        if(obj instanceof java.util.Map) {
            obj.collectEntries { k, v ->
                if (v != null){
                    Object newV = transformJson(v, f)
                    if (newV != [:])
                    {
                        f(k, newV)
                    } else {
                        [:]
                    }
                } else {
                    [:]
                }
            }
        } else if(obj instanceof java.util.Collection) {
            java.util.Collection newList = obj.collect{ item ->
                transformJson(item, f)
            }
            (newList.isEmpty()) ? [:] : newList
        } else {
            obj
        }
    }
}