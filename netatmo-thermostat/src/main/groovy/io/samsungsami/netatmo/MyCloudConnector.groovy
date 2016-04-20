package io.samsungsami.netatmo

import cloud.artik.cloudconnector.api_v1.*
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.scalactic.*
import scala.Option

import static java.net.HttpURLConnection.*

class MyCloudConnector extends CloudConnector {
    def JsonSlurper slurper = new JsonSlurper()
    //ctx.parameters().netatmoProduct == "weatherstation"|"thermostat"
    static final String endpoint = "https://api.netatmo.com/api/"
    static final String stationEndpoint = "${endpoint}getstationsdata"
    static final deviceKeys = [ "deviceId", "station_name", "type", "wifi_status" ]
    static final moduleKeys = [ "moduleId", "module_name", "type", "rf_status", "battery_vp", "battery_percent" ]
    static final setpointKeys = [ "setpoint", "setpoint_temp", "setpoint_endtime", "setpoint_mode" ]
    static final measuredKeys = [ "measured", "time", "temp", "setpoint_temp" ]
    static final modeKeysExceptManual = [ "program", "away", "hg", "off", "max" ]

    def Or<RequestDef, Failure> signAndPrepare(Context ctx, RequestDef req, DeviceInfo info, Phase phase) {
        Map params = [:]
        params.putAll(req.queryParams())
        switch (phase) {
            case Phase.refreshToken:
                params.put("client_id", ctx.clientId())
                params.put("client_secret", ctx.clientSecret())
                params.put(["refresh_token", info.credentials().refreshToken().get()])
                params.put("grant_type", "refresh_token")
                new Good(req.withBodyParams(params).withQueryParams([:]))
                break
            case Phase.subscribe:
            case Phase.unsubscribe:
            case Phase.undef:
            case Phase.fetch:
                params.put(["access_token", info.credentials().token()])
                new Good(req.withQueryParams(params))
                break
            default:
                super.signAndPrepare(ctx, req, info, phase)
        }
    }

    @Override
    def Or<ActionResponse, Failure> onAction(Context ctx, ActionDef action, DeviceInfo dInfo) {
        switch(ctx.parameters().netatmoProduct) {
            case "weatherstation":
                def req = new RequestDef("${endpoint}getstationsdata")
                switch (action.name) {
                    case "getAllData":
                        return new Good(new ActionResponse([new ActionRequest([req])]))
                    case "getData":
                        def json = slurper.parseText(action.params)
                        def keyAndParams = [["device_Id", json.stationId]]
                        return buildActionResponse(req, keyAndParams)
                    default:
                        return new Bad(new Failure("unsupported action for netatmo:" + action.name))
                }        
            break
            case "thermostat":
                def json = slurper.parseText(action.params? action.params.trim(): "{}")
                def request
                switch (action.name) {
                    case "getAllData":
                        request = new RequestDef("${ctx.parameters().endpoint}/getthermostatsdata")
                    break
                    case "getData":
                        def querys = ["device_id": json.deviceId]
                        if (nullValueCheck(querys)) {
                            return new Bad(new Failure("Null value in " + querys))
                        }
                        request = stringifiedReqFromMap(querys, "${ctx.parameters().endpoint}/getthermostatsdata")
                    break
                    case "setTemperatureDuring":
                        def querys = [  "device_id": json.deviceId,
                                        "module_id": json.moduleId,
                                        "setpoint_mode": "manual",
                                        "setpoint_endtime": ctx.now().intdiv(1000) + json.duration * 60,
                                        "setpoint_temp": json.temp
                                    ] 
                        if (nullValueCheck(querys)) {
                            return new Bad(new Failure("Null value in " + querys))
                        }
                        request = stringifiedReqFromMap(querys, "${ctx.parameters().endpoint}/setthermpoint")
                    break
                    //setTemperature by default during 12h
                    case "setTemperature":
                        def querys = [  "device_id": json.deviceId,
                                        "module_id": json.moduleId,
                                        "setpoint_mode": "manual",
                                        "setpoint_endtime": ctx.now().intdiv(1000) + 12 * 3600,
                                        "setpoint_temp": json.temp
                                    ]
                        if (nullValueCheck(querys)) {
                            return new Bad(new Failure("Null value in " + querys))
                        }
                        request = stringifiedReqFromMap(querys, "${ctx.parameters().endpoint}/setthermpoint")
                    break
                    //For mode "max", the setpoint_endtime is hard-coded as now() + 12h.
                    case "setMode":
                        if (nullValueCheck([json.deviceId, json.moduleId, json.mode])) {
                            return new Bad(new Failure("Null value in " + querys))
                        }
                        if (!modeKeysExceptManual.contains(json.mode)) {
                            return new Bad(new Failure("unsupported mode:" + json.mode))
                        }
                        def querys = [  "device_id": json.deviceId,
                                        "module_id": json.moduleId,
                                        "setpoint_mode": json.mode,
                                        "setpoint_endtime": (json.mode == "max")? ctx.now().intdiv(1000) + 12 * 3600: null
                                    ].findAll { k, v -> v!=null }
                        request = stringifiedReqFromMap(querys, "${ctx.parameters().endpoint}/setthermpoint")
                    break
                    default:
                        return new Bad(new Failure("unsupported action for netatmo:" + action.name))
                }
                return new Good(new ActionResponse([new ActionRequest([request])]))    
            break
            default:
                return new Bad(new Failure("unsupported ctx.parameters().netatmoProduct: " + ctx.parameters().netatmoProduct))
        }
        
    }


    @Override
    def Or<List<Event>, Failure> onFetchResponse(Context ctx, RequestDef req, DeviceInfo info, Response res) {
        switch (res.status) {
            case HTTP_OK:
                def json = slurper.parseText(res.content()? res.content().trim(): "{}")
                if (json.status != "ok") {
                    return new Bad(new Failure("receiving response with invalid status ${json.status} … ${res}"))
                }
                def ts = json.time_server
                switch (ctx.parameters().netatmoProduct) {
                    case "weatherstation":
                        def events = json.body.devices.collectMany { data ->
                            def netatmoId = data._id
                            def createEvent = { key, jsonEvent ->
                                new Event(ts * 1000L,
                                        JsonOutput.toJson(
                                                ["netatmoId": netatmoId,
                                                 (key): jsonEvent]
                                        ).trim())
                            }
                            def dataFiltered = transformJson(data, { k, v ->
                                switch (k) {
                                    case "temp_trend": return ["tempTrend": (v)]
                                    case "max_temp": return ["maxTemp": (v)]
                                    case "min_temp": return ["minTemp": (v)]
                                    case "date_min_temp": return ["dateMinTemp": (v)]
                                    case "date_max_temp": return ["dateMaxTemp": (v)]
                                    case "Temperature": return ["temp": (v)]
                                    case "location": return ["lat": v[0], "long": v[1]]
                                    case "data_type": return ["dataType": v.join(",")]
                                    default: return [(k): (v)]
                                }
                            })
                            def moduleEvents = dataFiltered?.modules?.collect { moduleData ->
                                createEvent("module", transformJson(moduleData, { k, v ->
                                    switch (k) {
                                        case "type": return ["moduleType": (v)]
                                        default: return [(k): (v)]
                                    }
                                }))
                            }
                            def stationEvent = [
                                    createEvent("station", transformJson(dataFiltered, { k, v ->
                                        switch (k) {
                                            case "modules": return [:]
                                            case "type": return ["stationType": (v)]
                                            default: return [(k): (v)]
                                        }
                                    }))
                            ]
                            stationEvent + moduleEvents
                        }
                        return new Good(events)
                        break
                    case "thermostat":
                        def devicesModulesMixte = json?.body?.devices?.collectMany { oneDevice ->
                            def modules = oneDevice.remove('modules') //no more key as "modules"
                            def deviceRenamed = renameJsonWithMap(oneDevice, ["_id":"deviceId"])
                            def moduleRenamed = renameJsonWithMap(modules, [
                                "_id": "moduleId",
                                "temperature": "temp"
                                ])
                            [deviceRenamed] + moduleRenamed
                        }
                        def mixteFiltered = filterByAllowedKeys( devicesModulesMixte, 
                            (deviceKeys + moduleKeys + setpointKeys + measuredKeys).unique()
                            )
                        def events = mixteFiltered.collect { obj ->
                            eventFromTsInSecond(ts, obj)
                        }
                        return new Good(events)
                        break
                    default:
                        return new Bad(new Failure("Unknown netatmoProduct: ${ctx.parameters().netatmoProduct}. receiving Responsse ${res} from unknown request ${req}"))
                }
            break
            default:
                return new Bad(new Failure("[${info.did}] onFetchResponse got status http status : ${res.status()}) with content: ${res.content()}"))
        }
    }


    def buildActionResponse(RequestDef req, List<List<String>> keyAndParams) {
        keyAndParams.collect { keyAndParam ->
            def param = keyAndParam[1]
            if (param == null) {
                return new Bad(new Failure("unsupported action for netatmo getData without " + keyAndParam[1]))
            }
            def key = keyAndParam[0]
            req = req.addQueryParams([(key): param])
        }
        new Good(new ActionResponse([new ActionRequest([req])]))
    }

    def nullValueCheck(obj) {
        switch(obj) {
            case Map:
                return obj.values().any{it == null}? true: false
            break
            case List:
                return obj.any{it == null}? true: false
            break
            default:
                return false
            break
        }
    }

    def stringifiedReqFromMap(map, url) {
        new RequestDef(url)
            .withQueryParams(
                map.collectEntries { k, v ->
                    [(k): v.toString()]
                }
            )
    }

    def transformJson(obj, f) {
        if (obj instanceof java.util.Map) {
            obj.collectEntries { k, v ->
                (v != null) ? f(k, transformJson(v, f)) : [:]
            }
        } else if (obj instanceof java.util.Collection) {
            obj.collect { item ->
                transformJson(item, f)
            }
        } else {
            obj
        }
    }

    def eventFromTsInSecond (ts, obj) {
        return new Event(ts * 1000L, JsonOutput.toJson(obj).trim())
    }

    def filterByAllowedKeys(obj, keepingKeys) {
        transformJson(obj, { k, v ->
            keepingKeys.contains(k)? [(k): (v)]: [:]
        })
    }

    def renameJsonWithMap(obj, renameMap) {
        transformJson(obj, { k, v ->
            renameMap.keySet().contains(k)? [(renameMap[k]): v]: [(k): v]
        })
    }

}