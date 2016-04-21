package io.samsungsami.philips

import cloud.artik.cloudconnector.api_v1.*
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.scalactic.*
import org.apache.commons.codec.binary.Base64

import java.nio.charset.StandardCharsets;
import static java.net.HttpURLConnection.*

class MyCloudConnector extends CloudConnector {
    static final allowedKeys = [
            "alert",
            "bri",
            "ct", "colormode",
            "effect",
            "hue",
            "modelid",
            "name",
            "on",
            "pointsymbol",
            "reachable",
            "sat","state","swversion",
            "type",
            "xy",
            "1", "2", "3", "4", "5", "6", "7", "8"
    ]
    static final endpoint = "https://api.meethue.com"
    static final brigdeEndpoint = "${endpoint}/v1/bridges"

    def JsonSlurper slurper = new JsonSlurper()

    def Or<RequestDef, Failure> signAndPrepare(Context ctx, RequestDef req, DeviceInfo info, Phase phase) {

        switch (phase) {
            case Phase.getOauth2Code:
                req.addQueryParams(["appid": ctx.parameters.appId(), "response_type": "code", "deviceid": "ARTIK Cloud"])
                return new Good(req)
            case Phase.refreshToken:
            case Phase.getOauth2Token:
                def secretBase64 = authHeaderBase64(ctx.clientId(), ctx.clientSecret())
                return new Good(req.withHeaders(["Authorization": "Basic " + secretBase64]))
            case Phase.undef:
            case Phase.subscribe:
            case Phase.unsubscribe:
            case Phase.fetch:
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
                return philipsApiActionResponse(urls, params)
                break
            case "setTemperatureInFahrenheit":
                def urls = [
                  ["root": "${ctx.parameters().endpoint}/devices/${ctx.parameters().productType}s"],
                  ["deviceId": json.deviceId]
                ]
                def params = ["target_temperature_f": json.temp]
                return philipsApiActionResponse(urls, params)
                break
            case "setHome":
                def urls = [
                  ["root": "${ctx.parameters().endpoint}/structures"],
                  ["structureId": json.structureId]
                ]
                def params = ["away": "home"]
                return philipsApiActionResponse(urls, params)
                break
            case "setAway":
                def urls = [
                  ["root": "${ctx.parameters().endpoint}/structures"],
                  ["structureId": json.structureId]
                ]
                def params = ["away": "away"]
                return philipsApiActionResponse(urls, params)
                break
            default:
                return new Bad(new Failure("unsupported action for philips:" + action.name))
        }
    }

    @Override
    def Or<List<Event>, Failure> onFetchResponse(Context ctx, RequestDef req, DeviceInfo info, Response res) {
        switch (res.status) {
            case HTTP_OK:
                def json = slurper.parseText(res?.content ?: "{}")
                switch (req.url) {
                    case brigdeEndpoint:
                        return new Good(new Event(ctx.now(),updateBrigde(json) , EventType.user))

                    default:
                        if (req.url.contains(brigdeEndpoint)){
                            return new Good(new Event(ctx.now(),updateLight(json) , EventType.user))
                        } else {
                            return new Bad(new Failure("[${info.did}] onFetchResponse response to unknown req" + req))
                        }
                }
            default:
                return new Bad(new Failure("[${info.did}] onFetchResponse got status http status : ${res.status()}) with content: ${res.content()}"))
        }
    }

    private def refreshReq(DeviceInfo dInfo) {
        [new RequestDef(brigdeEndpoint)] + refreshLightReq(dInfo)
    }
    private def refreshLightReq(DeviceInfo dInfo) {
        data = slurper.parseText(dInfo.userData().getOrElse("{}"))
        data.bridges.collectEntries{ name, bridgeId ->
            new RequestDef("${brigdeEndpoint}/${bridgeId}/lights")
        }
    }

    private def updateBrigde(Map json, DeviceInfo dInfo) {
        def storedData = slurper.parseText(dInfo.userData().getOrElse("{}"))
        def bridges = json.collect{bridge -> [(bridge.name):(bridge.id)] }
        JsonOutput.toJson(storedData.put("bridges", bridges))
    }
    private def updateLight(Map json, DeviceInfo dInfo) {
        def storedData = slurper.parseText(dInfo.userData().getOrElse("{}"))
        def lights = storedData.lights
        def i = 0
        while (json.get(i.toString()) != null){
            lights.put(i.toString(), json.get(i.toString()).name)

        }
        JsonOutput.toJson(storedData.put("lights", lights))
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
    def philipsApiActionResponse(List<Map<String, Object>> urlKeyAndNodes, Map contentParams) {
        contentParams.each { k, v ->
            if (v == null) {
                return new Bad(new Failure("Null value in Action parameter: " + (k) + " : " + v))
            }
        }
        def urlNodes = urlKeyAndNodes.collect { keyAndParam ->
            def param = keyAndParam.values()[0]
            if (param == null) {
                return new Bad(new Failure("Null value in Action parameter:  " + keyAndParam.keySet() + " : " + param))
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
            transformJson(json, { k, v ->
                allowedKeys.contains(k)? [(k): v]: [:]
            })
        ).trim()
    }

    /**
     * The Authorization header must be set to Basic followed by a space,
     * then the Base64 encoded string of your application's client id and secret concatenated with a colon.
     * For example, the Base64 encoded string, Y2xpZW50X2lkOmNsaWVudCBzZWNyZXQ=, is decoded as "client_id:client secret".
     */
    private def authHeaderBase64(String clientId, String clientSecret) {
        def secret = (clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8)
        return new String(Base64.encodeBase64(secret), StandardCharsets.UTF_8)
    }
}
