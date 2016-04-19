package io.samsungsami.nest

import cloud.artik.cloudconnector.api_v1.*
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.scalactic.*

import static java.net.HttpURLConnection.*

class MyCloudConnector extends CloudConnector {
    static final allowedKeys = [
            "away", "away_temperature_high_c", "away_temperature_low_c", "ambient_temperature_c",
            "can_cool", "can_heat",
            "device_id",
            "fan_timer_active", "fan_timer_timeout",
            "has_fan", "has_leaf", "hvac_mode", "humidity", "hvac_state",
            "is_online", "is_using_emergency_heat",
            "last_connection",
            "name", "name_long",
            "structure_name",
            "target_temperature_c", "target_temperature_high_c", "target_temperature_low_c"
    ]
    def JsonSlurper slurper = new JsonSlurper()

    def Or<RequestDef, Failure> signAndPrepare(Context ctx, RequestDef req, DeviceInfo info, Phase phase) {
        ctx.debug("signAndPrepare Started")
        probe(ctx, info, "info")
        ctx.debug('phase: ' + phase)
        return new Good(req.addHeaders(['Authorization':'Bearer ' + info.credentials.token]))
    }

    @Override
    def Or<ActionResponse, Failure> onAction(Context ctx, ActionDef action, DeviceInfo info) {
        ctx.debug("onAction Started")
        def json = slurper.parseText(action.params? action.params.trim(): "{}")
        switch (action.name) {
            case "getAllData":
                return new Good(new ActionResponse([new ActionRequest([new RequestDef("${ctx.parameters().endpoint}")])]))
            case "setTemperature":
                def querys = [  "did": json.deviceId,
                                "target_temperature_c": json.temp
                            ]
                return nestJsonActionResponse(querys, "${ctx.parameters().endpoint}/devices/${ctx.parameters().productType}s/${querys.did}", ctx)
                break
            case "setTemperatureInFahrenheit":
                def querys = [  "did": json.deviceId,
                                "target_temperature_f": json.temp
                            ]
                return nestJsonActionResponse(querys, "${ctx.parameters().endpoint}/devices/${ctx.parameters().productType}s/${querys.did}", ctx)
                break
            case "setHome":
                def querys = [  "sid": json.buildingId,
                                "away": "home"
                            ]
                return nestJsonActionResponse(querys, "${ctx.parameters().endpoint}/structures/${querys.sid}", ctx)
                break
            case "setAway":
                def querys = [  "sid": json.buildingId,
                                "away": "away"
                            ]
                return nestJsonActionResponse(querys, "${ctx.parameters().endpoint}/structures/${querys.sid}", ctx)
                break
            default:
                return new Bad(new Failure("unsupported action for nest:" + action.name))
        }
    }

    @Override
    Or<RequestDef, Failure> fetch(Context ctx, RequestDef req, DeviceInfo info) {
        probe(ctx, req, "req in Fetch")
        new Good(req)
    }

    @Override
    def Or<List<Event>, Failure> onFetchResponse(Context ctx, RequestDef req, DeviceInfo info, Response res) {
        probe(ctx, res, "res")
        switch (res.status) {
            case HTTP_OK:
                return new Good([new Event(42L, res.content)])
            default:
                return new Bad(new Failure("[${info.did}] onFetchResponse got status http status : ${res.status()}) with content: ${res.content()}"))
        }
    }

    // Copy-pasted from sami-cloudconnector-samples/open-weather-map/src/main/groovy/io/samsungsami/openWeatherMap/MyCloudConnector.groovy -> transformJson
    // applyToMessage(msg, f) will remove all empty values
    def applyToMessage(msg, f) {
        if (msg instanceof java.util.Map) {
            msg.collectEntries { k, v ->
                if (v != null) {
                    def newV = applyToMessage(v, f)
                    if (newV != [:]) {
                        f(k, newV)
                    } else {
                        [:]
                    }
                } else {
                    [:]
                }
            }
        } else if (msg instanceof java.util.Collection) {
            java.util.Collection newList = msg.collect { item ->
                applyToMessage(item, f)
            }
            (newList.isEmpty()) ? [:] : newList
        } else {
            msg
        }
    }

    def filterByAllowedKeys(obj, keepingKeys) {
        applyToMessage(obj, { k, v ->
            keepingKeys.contains(k)? [(k): v]: [:]
        })
    }

    def nestJsonActionResponse(paramsMap, url, ctx) {
        if (nullValueCheck(paramsMap)) {
            return new Bad(new Failure("Null value in " + paramsMap))
        }
        def paramsWithoutId = paramsMap.findAll{ k, v -> !k.endsWith('id') }
        probe(ctx, paramsWithoutId, "paramsWithoutId")
        def request = new RequestDef(url).withMethod(HttpMethod.Put)
                        .withContent(JsonOutput.toJson(paramsWithoutId), "application/json")
        return new Good(new ActionResponse([new ActionRequest([request])]))
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


    def probe(ctx, obj="", objName = "") {
        ctx.debug(objName + " " + obj?.getClass() + "\n" + obj)
    }
}
