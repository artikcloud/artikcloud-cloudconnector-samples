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
        Map params = [:]
        params.putAll(req.queryParams())
        switch (phase) {
            case Phase.undef:
            case Phase.subscribe:
            case Phase.unsubscribe:
            case Phase.fetch:
            return new Good(req.addHeaders(["Authorization": "Bearer " + info.credentials.token]))
                break
            case Phase.refreshToken:
            case Phase.getOauth2Token:
            default:
                super.signAndPrepare(ctx, req, info, phase)
        }
    }

    @Override
    def Or<ActionResponse, Failure> onAction(Context ctx, ActionDef action, DeviceInfo info) {
        def json = slurper.parseText(action.params? action.params.trim(): "{}")
        switch (action.name) {
            case "getAllData":
                return new Good(new ActionResponse([new ActionRequest([new RequestDef("${ctx.parameters().endpoint}")])]))
            case "setTemperature":
                def urls = [
                  ["root": "${ctx.parameters().endpoint}/devices/${ctx.parameters().productType}s"],
                  ["deviceId": json.deviceId]
                ]
                def params = ["target_temperature_c": json.temp]
                return nestApiActionResponse(urls, params)
                break
            case "setTemperatureInFahrenheit":
                def urls = [
                  ["root": "${ctx.parameters().endpoint}/devices/${ctx.parameters().productType}s"],
                  ["deviceId": json.deviceId]
                ]
                def params = ["target_temperature_f": json.temp]
                return nestApiActionResponse(urls, params)
                break
            case "setHome":
                def urls = [
                  ["root": "${ctx.parameters().endpoint}/structures"],
                  ["structureId": json.structureId]
                ]
                def params = ["away": "home"]
                return nestApiActionResponse(urls, params)
                break
            case "setAway":
                def urls = [
                  ["root": "${ctx.parameters().endpoint}/structures"],
                  ["structureId": json.structureId]
                ]
                def params = ["away": "away"]
                return nestApiActionResponse(urls, params)
                break
            default:
                return new Bad(new Failure("unsupported action for nest:" + action.name))
        }
    }

    @Override
    def Or<List<Event>, Failure> onFetchResponse(Context ctx, RequestDef req, DeviceInfo info, Response res) {
        switch (res.status) {
            case HTTP_OK:
                def json = slurper.parseText(res?.content ?: "{}")
                //json/devices/thermostats/Map<THERMO_ID, THERMO_VALUE>
                def events = json?.devices?.get("${ctx.parameters().productType}s")?.values()?.collect { device ->
                    def extraStructure = json?.structures?.get("${device.structure_id}")
                    device.put('structure', extraStructure)
                    new Event(ctx.now(), outputJson(device))
                } ?: []
                return new Good(events)
            default:
                return new Bad(new Failure("[${info.did}] onFetchResponse got status http status : ${res.status()}) with content: ${res.content()}"))
        }
    }

    // Copy-pasted from sami-cloudconnector-samples/open-weather-map/src/main/groovy/io/samsungsami/openWeatherMap/MyCloudConnector.groovy -> transformJson
    // applyToMessage(msg, f) will remove all empty values
    private def applyToMessage(msg, f) {
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

    //In the entering List<Map>, this Map should include only 1 key-value!, Using List<Map> to ensure collect in order
    def nestApiActionResponse(List<Map<String, Object>> urlKeyAndNodes, Map contentParams) {
        contentParams.each { k, v ->
            if (v == null) {
                return new Bad(new Failure("Null value in query which item is: " + (k) + " : " + v))
            }
        }
        def urlNodes = urlKeyAndNodes.collect { keyAndParam ->
            def param = keyAndParam.values()[0]
            if (param == null) {
                return new Bad(new Failure("Null value in query which item is: " + keyAndParam.keySet() + " : " + keyAndParam.values()))
            }
            return param
        }      
        def request = new RequestDef(urlNodes.join("/"))
                        .withMethod(HttpMethod.Put)
                        .withContent(JsonOutput.toJson(contentParams), "application/json")
        return new Good(new ActionResponse([new ActionRequest([request])]))
    }
    
    private def outputJson(json) {
        JsonOutput.toJson(
            applyToMessage(json, { k, v ->
                allowedKeys.contains(k)? [(k): v]: [:]
            })
        ).trim()
    }
}
