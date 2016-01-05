// Sample CloudConnector, that can be used as a boostrap to write a new CloudConnector.
// Every code is commented, because everything is optional.
// The class can be named as you want no additional import allowed
// See the javadoc/scaladoc of com.samsung.sami.cloudconnector.api.CloudConnector
package io.samsungsami.jawbone

import com.samsung.sami.cloudconnector.api_v1.*
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.joda.time.*
import org.scalactic.*

import static java.net.HttpURLConnection.*

//TODO implement unsubscription
//@CompileStatic
class MyCloudConnector extends CloudConnector {
    def API_ENDPOINT_URL = "https://jawbone.com/nudge/api/v.1.1/"
    def PUBSUB_ENDPOINT_URL = API_ENDPOINT_URL + "users/@me/pubsub"

    def ACTION_TYPE_MATCHER =
            //Key = "type" parameter in callback request
            //Value = endpoint Url to use to fetch event data.
            ["move" : "moves",
             "sleep" : "sleeps",
             "body" : "body_events",
             "workout": "workouts",
             "meal" :  "meals",
             "mood" :  "mood"]

    JsonSlurper slurper = new JsonSlurper()

    @Override
    def Or<RequestDef, Failure> signAndPrepare(Context ctx, RequestDef req, DeviceInfo info, Phase phase) {
        Map params = [:]
        params.putAll(req.queryParams())
        switch (phase) {
            case Phase.refreshToken:
                params.put("client_id", ctx.clientId)
                params.put("client_secret", ctx.clientSecret)
                //TODO check if params should be in QueryString or Body ??
                //doc: https://jawbone.com/up/developer/authentication
                return new Good(req.withQueryParams(params).withMethod(HttpMethod.Post))
            case Phase.undef:
            case Phase.subscribe:
            case Phase.unsubscribe:
            case Phase.fetch:
                return new Good(req.addHeaders(["Authorization": "Bearer " + info.credentials.token]))
            default:
                return super.signAndPrepare(ctx, req, info, phase)
        }
    }

    @Override
    def Or<List<RequestDef>, Failure> subscribe(Context ctx, DeviceInfo info) {
        def req = new Good([
                new RequestDef(PUBSUB_ENDPOINT_URL).
                        withQueryParams([
                                "webhook" : String.format("%s%s/thirdpartynotifications?samiDeviceId=%s", ctx.parameters().get("webhookUrl"),  ctx.cloudId(), info.did())
                        ]).
                        withContent("", "application/x-www-form-urlencoded; charset=UTF-8").
                        withMethod(HttpMethod.Post)
        ])
        return req
    }

    @Override
    def Or<NotificationResponse, Failure> onNotification(Context ctx, RequestDef inReq) {
        def did = inReq.queryParams().get("samiDeviceId")
        if (did == null) {
            ctx.debug("Bad notification (where is did in following req : ) " + inReq)
            return new Bad(new Failure("Impossible to recover device id from token request."))
        }

        /*
        Jawbone Pub sub API
        https://jawbone.com/up/developer/pubsub
        We are including a secret hash with each notification so you can confirm it came from Jawbone.
        The secret hash is a SHA-256 of the concatenated string of your client ID followed by the app secret.
         */
        def jawboneCallbackSecretHash = CryptographicHelper.sha256(ctx.clientId() + ctx.clientSecret())

        def content = inReq.content().trim()
        def json = slurper.parseText(content)
        def PUSH_EVENT = ["enter_sleep_mode", "exit_sleep_mode", "enter_stopwatch_mode", "exit_stopwatch_mode"]
        ctx.debug("json from callback : " + json)
        for(e in json.events){
            if (e.secret_hash == null || e.secret_hash != jawboneCallbackSecretHash){
                ctx.debug("Invalid secret hash for callback request $inReq ; expected : $jawboneCallbackSecretHash")
                return new Bad(new Failure("Invalid secret hash"))
            }
        }
        def dataToPush = json.events.findAll { PUSH_EVENT.contains(it.action) }.collect {e -> JsonOutput.toJson(e)}
        ctx.debug("Data to push : " + dataToPush)

        def dataToFetch = json.events.findAll { it.containsKey("type") && ACTION_TYPE_MATCHER.keySet().contains(it.type) }.collect { e ->
            new RequestDef(String.format("%s%s/%s", API_ENDPOINT_URL, ACTION_TYPE_MATCHER.get(e.type), e.event_xid))
        }

        ctx.debug("Data provided : " + dataToFetch)

        return new Good(new NotificationResponse([new ThirdPartyNotification(new BySamiDeviceId(did), dataToFetch, dataToPush)]))
    }

    // 5. Parse and check (authorisation) pushed data (data come from Notification and can be transformed)
    @Override
    def Or<List<Event>, Failure> onNotificationData(Context ctx, DeviceInfo info, String data) {
        def json = slurper.parseText(data)
        def builder = new groovy.json.JsonBuilder()
        def timestamp = json.timestamp * 1000L
        def payload
        switch (json.action) {
            case "enter_sleep_mode":
                payload = builder {
                    category "sleep_mode"
                    measuregrp ([builder {state true}])
                }
                break
            case "exit_sleep_mode":
                payload = builder {
                    category "sleep_mode"
                    measuregrp ([builder {state false}])
                }
                break
            case "enter_stopwatch_mode":
                payload = builder {
                    category "watch_mode"
                    measuregrp ([builder {state true}])
                }
                break
            case "exit_stopwatch_mode":
                payload = builder {
                    category "watch_mode"
                    measuregrp ([builder {state false}])
                }
                break
            default:
                return new Bad(new Failure("Unsupported action " + json.action))
        }
        return new Good([new Event(timestamp, JsonOutput.toJson(payload))])
    }

    @Override
    def Or<List<Event>, Failure> onFetchResponse(Context ctx, RequestDef req, DeviceInfo info, Response res) {
        def builder = new groovy.json.JsonBuilder()
        switch(res.status) {
            case HTTP_OK:
                def content = res.content().trim()
                if (content == "" || content == "OK") {
                    ctx.debug("Ignore response valid respond: '${res.content()}'")
                    return new Good(Empty.list())
                } else if (res.contentType().startsWith("application/json")) {
                    def json = slurper.parseText(content)
                    if (json.data.type == null)
                        return new Bad(new Failure("Can't recover type from request : " + req))
                    def payload = builder {
                        category json.data.type
                        measuregrp json
                    }
                    def ts
                    if (json.data.containsKey("time_updated"))
                        ts = json.data.time_updated
                    else if (json.data.containsKey("time_created"))
                        ts = json.data.time_created
                    else
                        return new Bad(new Failure("Unsupported timestamp in response ${res} ... ${res.contentType()}"))
                    ctx.debug("payload = " + JsonOutput.toJson(payload))
                    new Good([new Event(ts * 1000l, JsonOutput.toJson(payload))])

                } else {
                    return new Bad(new Failure("unsupported response ${res} ... ${res.contentType()}"))
                }
                break
            default:
                return new Bad(new Failure("http status : ${res.status()} is not OK (${HTTP_OK})"))
        }
    }
}