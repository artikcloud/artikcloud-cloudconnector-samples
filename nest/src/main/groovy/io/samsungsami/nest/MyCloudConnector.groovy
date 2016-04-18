package io.samsungsami.nest


import cloud.artik.cloudconnector.api_v1.*
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.scalactic.*

import static java.net.HttpURLConnection.*

class MyCloudConnector extends CloudConnector {
    static final String endpoint = "https://developer-api.nest.com"
    def JsonSlurper slurper = new JsonSlurper()


    def Or<RequestDef, Failure> signAndPrepare(Context ctx, RequestDef req, DeviceInfo info, Phase phase) {
        Map params = [:]
        params.putAll(req.queryParams())
        switch (phase) {
            case Phase.undef:
            case Phase.subscribe:
            case Phase.unsubscribe:
            case Phase.fetch:
            ctx.debug(info.credentials.token)
            return new Good(req.addHeaders(["Authorization": "Bearer " + info.credentials.token]))
                break
            case Phase.refreshToken:
            case Phase.getOauth2Token:
            default:
                super.signAndPrepare(ctx, req, info, phase)
        }
    }

    @Override
    def Or<ActionResponse, Failure> onAction(Context ctx, ActionDef action, DeviceInfo dInfo) {
        switch (action.name) {
            case "getAllData":
                return new Good(new ActionResponse([new ActionRequest([new RequestDef(endpoint + "/all")])]))
            default:
                return new Bad(new Failure("unsupported action for nest:" + action.name))
        }
    }


    @Override
    def Or<List<Event>, Failure> onFetchResponse(Context ctx, RequestDef req, DeviceInfo info, Response res) {
        switch (res.status) {
            case HTTP_OK:
                switch (req.url) {
                    case endpoint + "/all":
                        def json = slurper.parseText(res.content())


                        return new Good(Event(42L, json))
                    default:
                        return new Bad(new Failure("receiving Responsse ${res} fron unknown request ${req.req}"))
                }
            default:
                return new Bad(new Failure("[${info.did}] onFetchResponse got status http status : ${res.status()}) with content: ${res.content()}"))
        }
    }


    private def buildActionResponse(RequestDef req, List<List<String>> keyAndParams) {
        keyAndParams.collect { keyAndParam ->
            def param = keyAndParam[1]
            if (param == null) {
                return new Bad(new Failure("unsupported action for openWeatherMap getData without " + keyAndParam[1]))
            }
            def key = keyAndParam[0]
            req = req.addQueryParams([(key): param])
        }
        new Good(new ActionResponse([new ActionRequest([req])]))
    }

    private def transformJson(obj, f) {
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
}
