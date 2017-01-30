package cloudconnector

import cloud.artik.cloudconnector.api_v1.*
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.joda.time.*
import org.scalactic.*
import scala.Option

import static java.net.HttpURLConnection.*

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
                params.put("client_id", ctx.clientId())
                params.put("client_secret", ctx.clientSecret())
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
                new RequestDef(PUBSUB_ENDPOINT_URL).withMethod(HttpMethod.Delete),
                new RequestDef("${API_ENDPOINT_URL}users/@me")
        ])
        return req
    }

    @Override
    def Or<Option<DeviceInfo>,Failure> onSubscribeResponse(Context ctx, RequestDef req,  DeviceInfo info, Response res) {
        if (req.url().endsWith("@me")) {
            def json = slurper.parseText(res.content)
            new Good(Option.apply(info.withExtId(json.meta.user_xid)))
        } else {
            new Good(Option.apply(null))
        }
    }

    // useless but keep for backward compatibility security
    @Override
    def Or<List<RequestDef>, Failure> unsubscribe(Context ctx, DeviceInfo info) {
        return new Good([new RequestDef(PUBSUB_ENDPOINT_URL).withMethod(HttpMethod.Delete)])
    }

    @Override
    def Or<NotificationResponse, Failure> onNotification(Context ctx, RequestDef req) {
        if (req.url.endsWith("thirdpartynotifications/postsubscription")) {
            return new Good(new NotificationResponse([]))
        }
        def content = req.content().trim()
        def json = slurper.parseText(content)

        def did = req.queryParams().get("samiDeviceId")
        def selector = (did != null)
            ? new ByDid(did)
            : (json?.events?.length?:0 > 0)
            ? new ByExtId(json.events[0].user_xid)
            : null

        if (selector == null) {
            ctx.debug("Bad notification (where is did in following req : ) " + req)
            return new Bad(new Failure("Impossible to recover device id from token request."))
        }

        /*
        Jawbone Pub sub API
        https://jawbone.com/up/developer/pubsub
        We are including a secret hash with each notification so you can confirm it came from Jawbone.
        The secret hash is a SHA-256 of the concatenated string of your client ID followed by the app secret.
         */
        def jawboneCallbackSecretHash = CryptographicHelper.sha256(ctx.clientId() + ctx.clientSecret())
        def PUSH_EVENT = ["enter_sleep_mode", "exit_sleep_mode", "enter_stopwatch_mode", "exit_stopwatch_mode"]
        ctx.debug("json from callback : " + json)
        for(e in json.events){
            if ((e.secret_hash == null || e.secret_hash != jawboneCallbackSecretHash) && (json.secret_hash != jawboneCallbackSecretHash)){
                ctx.debug("Invalid secret hash for callback request $req ; expected : $jawboneCallbackSecretHash")
                return new Bad(new Failure("Invalid secret hash"))
            }
        }
        def dataToPush = json.events.findAll { PUSH_EVENT.contains(it.action) }.collect {e -> JsonOutput.toJson(e)}
        ctx.debug("Data to push : " + dataToPush)

        def dataToFetch = json.events.findAll { it.containsKey("type") && ACTION_TYPE_MATCHER.keySet().contains(it.type) }.collect { e ->
            new RequestDef(String.format("%s%s/%s", API_ENDPOINT_URL, ACTION_TYPE_MATCHER.get(e.type), e.event_xid))
                    .addQueryParams(['type': e.type])
        }

        ctx.debug("Data provided : " + dataToFetch)

        return new Good(new NotificationResponse([new ThirdPartyNotification(selector, dataToFetch, dataToPush)]))
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
                    measuregrp (builder {state true})
                }
                break
            case "exit_sleep_mode":
                payload = builder {
                    category "sleep_mode"
                    measuregrp (builder {state false})
                }
                break
            case "enter_stopwatch_mode":
                payload = builder {
                    category "watch_mode"
                    measuregrp (builder {state true})
                }
                break
            case "exit_stopwatch_mode":
                payload = builder {
                    category "watch_mode"
                    measuregrp (builder {state false})
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
                    def type = req.queryParams()['type']
                    if (type == null)
                        return new Bad(new Failure("Can't recover type from request : " + req + " with response " + res))
                    def payload = builder {
                        category type
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
