package io.samsungsami.nest

import cloud.artik.cloudconnector.api_v1.*
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.scalactic.*

import static java.net.HttpURLConnection.*

class MyCloudConnector extends CloudConnector {
    static final allowedKeys = [
            "away_temperature_high_c", "away_temperature_low_c", "ambient_temperature_c",
            "can_cool", "can_heat",
            "device_id",
            "fan_timer_active", "fan_timer_timeout",
            "has_fan", "has_leaf", "hvac_mode", "humidity", "hvac_state",
            "is_online", "is_using_emergency_heat",
            "last_connection",
            "name", "name_long",
            "structure_id",
            "target_temperature_c", "target_temperature_high_c", "target_temperature_low_c",
            "structure", "away", "name"
    ]

    def JsonSlurper slurper = new JsonSlurper()

    def Or<RequestDef, Failure> signAndPrepare(Context ctx, RequestDef req, DeviceInfo info, Phase phase) {
        ctx.debug("signAndPrepare Started")
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
                def querys = [
                    "did": json.deviceId,
                    "target_temperature_c": json.temp
                ]
                return nestJsonActionResponse(querys, "${ctx.parameters().endpoint}/devices/${ctx.parameters().productType}s/${querys.did}", ctx)
                break
            case "setTemperatureInFahrenheit":
                def querys = [
                    "did": json.deviceId,
                    "target_temperature_f": json.temp
                ]
                return nestJsonActionResponse(querys, "${ctx.parameters().endpoint}/devices/${ctx.parameters().productType}s/${querys.did}", ctx)
                break
            case "setHome":
                def querys = [
                    "sid": json.structureId,
                    "away": "home"
                ]
                return nestJsonActionResponse(querys, "${ctx.parameters().endpoint}/structures/${querys.sid}", ctx)
                break
            case "setAway":
                def querys = [
                    "sid": json.structureId,
                    "away": "away"
                ]
                return nestJsonActionResponse(querys, "${ctx.parameters().endpoint}/structures/${querys.sid}", ctx)
                break
            default:
                return new Bad(new Failure("unsupported action for nest:" + action.name))
        }
    }

    @Override
    def Or<List<Event>, Failure> onFetchResponse(Context ctx, RequestDef req, DeviceInfo info, Response res) {
        switch (res.status) {
            case HTTP_OK:
                def json = slurper.parseText(res.content? res.content.trim(): "{}")
                ctx.debug("the line after json defined")
                def events = json?.devices?.get("${ctx.parameters().productType}s")?.values()?.collect { oneDevice ->
                    def extraStructure = json?.structures?.get("${oneDevice.structure_id}")
                    oneDevice.put('structure', extraStructure)
                    new Event(ctx.now(), outputJson(oneDevice))
                }
                if (events == null) {
                    events = Empty.list()
                }
                return new Good(events)
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
                    newV != [:]? f(k, newV): [:]
                } else {
                    [:]
                }
            }
        } else if (msg instanceof java.util.Collection) {
            java.util.Collection newList = msg.collect { item ->
                applyToMessage(item, f)
            }
            newList.isEmpty()? [:]: newList
        } else {
            msg
        }
    }

    def nestJsonActionResponse(paramsMap, url, ctx) {
        if (nullValueCheck(paramsMap)) {
            return new Bad(new Failure("Null value in " + paramsMap))
        }
        def paramsWithoutId = paramsMap.findAll{ k, v -> !k.endsWith('id') }
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
    
    def outputJson(json) {
        JsonOutput.toJson(
            applyToMessage(json, { k, v ->
                allowedKeys.contains(k)? [(k): v]: [:]
            })
        ).trim()
    }

}
