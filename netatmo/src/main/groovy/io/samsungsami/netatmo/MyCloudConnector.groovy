package io.samsungsami.netatmo

import com.samsung.sami.cloudconnector.api_v1.*
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import org.joda.time.format.*
import org.joda.time.*
import org.scalactic.*

import static java.net.HttpURLConnection.*

class MyCloudConnector extends CloudConnector {
    static final String endpoint = "https://api.netatmo.com/api/"
    static final String stationEndpoint = "${endpoint}getstationsdata"
    def JsonSlurper slurper = new JsonSlurper()



    def Or<RequestDef, Failure> signAndPrepare(Context ctx, RequestDef req, DeviceInfo info, Phase phase){
        Map params = [:]
        params.putAll(req.queryParams())
        switch (phase) {
            case Phase.subscribe:
            case Phase.unsubscribe:
            case Phase.undef:
            case Phase.fetch:
            case Phase.refreshToken:
                new Good(req.addQueryParams(["access_token": info.credentials().token()]))
                break
            default:
                super.signAndPrepare(ctx, req, info, phase)
        }
    }

    @Override
    def  Or<ActionResponse, Failure> onAction(Context ctx, ActionDef action, DeviceInfo dInfo) {
        def req = new RequestDef(stationEndpoint)
        DeviceSelector dSelector = new BySamiDeviceId(dInfo.did)
        def actionRequests = null
        switch (action.name) {
            case "getAllData":
                actionRequests = [new ActionRequest(dSelector,[req])]
                break
            case "getData":
                def json = slurper.parseText(action.params)
                def keyAndParams = [["device_Id", json.stationId]]
                return buildActionResponse(req, dSelector, keyAndParams)
                break
            default:
                return new Bad(new Failure("unsupported action for netatmo:" + action.name))
                break
        }

        new Good(new ActionResponse(actionRequests))
    }


    @Override
    def Or<List<Event>, Failure> onFetchResponse(Context ctx, RequestDef req, DeviceInfo info, Response res) {
        switch(res.status) {
            case HTTP_OK:
                switch(req.url) {
                    case stationEndpoint:
                        def json = slurper.parseText(res.content().trim())
                        if (json.status != "ok"){
                            return new Bad(new Failure("receiving response with invalid status ${json.status} … ${res}"))
                        }
                        def ts = json.time_server
                        def events = json.body.devices.collectMany{ data ->
                            def netatmoId = data._id
                            def createEvent = { key, jsonEvent ->
                                new Event(ts * 1000L,
                                        JsonOutput.toJson(
                                                ["netatmoId": netatmoId,
                                                 (key):jsonEvent]
                                        ).trim())
                            }

                            def moduleEvents = data?.modules?.collect{ moduleData ->
                                createEvent("module", transformJson(moduleData, { k, v ->
                                    switch (k) {
                                        case "type":        return ["moduleType": (v)]
                                        case "data_type":   return ["dataType": v.join("'")]
                                        default:            return [(k): (v)]
                                    }
                                }))
                            }
                            def stationEvent = [
                                    createEvent("station", transformJson(data, { k, v ->
                                        switch (k) {
                                            case "modules":     return [:]
                                            case "location":    return ["lat": v[0], "long": v[1]]
                                            case "temp_trend":  return ["tempTrend": (v)]
                                            case "max_temp":    return ["maxTemp": (v)]
                                            case "min_temp":    return ["minTemp": (v)]
                                            case "date_min_temp":   return ["dateMinTemp": (v)]
                                            case "date_max_temp":   return ["dateMaxTemp": (v)]
                                            case "Temperature": return ["temp": (v)]
                                            case "type":        return ["stationType": (v)]
                                            default:            return [(k): (v)]
                                        }
                                    }))
                            ]
                            stationEvent + moduleEvents
                        }
                        return new Good(events)
                        break
                    default:
                        return new Bad(new Failure("receiving Responsse ${res} fron unknown request ${req.req}"))
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
            req = req.addQueryParams([(key):param])
        }
        new Good(new ActionResponse([new ActionRequest(dSelector,[req])]))
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