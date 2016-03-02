package io.samsungsami.openWeatherMap

import com.samsung.sami.cloudconnector.api_v1.*
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.scalactic.*

import static java.net.HttpURLConnection.*

class MyCloudConnector extends CloudConnector {
    static final String currentWeatherUrl = "http://api.openweathermap.org/data/2.5/weather"
    def JsonSlurper slurper = new JsonSlurper()



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
        RequestDef req = new RequestDef(currentWeatherUrl).withQueryParams(["units":"metric"])
        switch (action.name) {
            case "getCurrentWeatherByCity":
                if (json.city == null) {
                    return new Bad(new Failure("unsupported action for openWeatherMap getData without city " + action.params))
                }
                String city = json.city
                if (json.countryCode != null){
                   city = city + "," + json.countryCode
                }
                req = req.addQueryParams(["q":city])
                return new Good(new ActionResponse([new ActionRequest([req])]))
                break
            case "getCurrentWeatherByGPSLocation":
                def keyAndParams = [["lat", json?.lat],
                                   ["lon",json?.long]]
                return buildActionResponse(req, dSelector, keyAndParams)
                break
            default:
                break
        }
        return new Bad(new Failure("unsupported action for openWeatherMap:" + action.name))
    }


    @Override
    def Or<List<Event>, Failure> onFetchResponse(Context ctx, RequestDef req, DeviceInfo info, Response res) {
        switch(res.status) {
            case HTTP_OK:
                switch(req.url) {
                    case currentWeatherUrl:
                        def json = slurper.parseText(res.content().trim())
                        def msgWithoutWeather = transformJson(json, {k,v ->
                            if (k == "lon")
                                ["long":(v)]
                            else if (k == "3h")
                                ["three_hours":(v)]
                            else if (k != "weather")
                                [(k): (v)]
                            else
                                [:]
                        })
                        def events = json?.weather.collect{ weatherData ->
                            def msg = msgWithoutWeather + ["weather": weatherData]
                            new Event(msgWithoutWeather.dt, JsonOutput.toJson(msg).trim())
                        }
                        return new Good(events)
                        break
                    default:
                        return new Bad(new Failure("receiving Responsse ${res} fron unknown request ${req}"))
                        break
                }
                break
            default:
                return new Bad(new Failure("[${info.did}] onFetchResponse got status http status : ${res.status()}) with content: ${res.content()}"))
        }
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
                (v != null) ? f(k, transformJson(v, f)) : [:]
            }
        } else if(obj instanceof java.util.Collection) {
            obj.collect{ item ->
                transformJson(item, f)
            }
        } else {
            obj
        }
    }
}