package io.samsungsami.instagram

import org.scalactic.*
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import scala.Option
import com.samsung.sami.cloudconnector.api.*
import static java.net.HttpURLConnection.*

//@CompileStatic
class MyCloudConnector extends CloudConnector {
    static final API_URL = "https://api.instagram.com/v1"
    static final PROFILE_URL = API_URL + "/users/self"
    static final ENDPOINT_URL = API_URL + "/users/self/media/recent"
    static final REQUEST_TIME_RANGE = 20 //in seconds
    JsonSlurper slurper = new JsonSlurper()

    @Override
    def Or<Option<RequestDef>, Failure> setup(Context ctx) {
        def req = new RequestDef(API_URL+ "/subscriptions/")
            .withMethod(HttpMethod.Post)
            .withBodyParams([
              'client_id':'9b26e76362c448db808fd604015eb8c3',
              'client_secret':'67a1670e71874d36a4e9c1052d74dd9d',
              'object':'user',
              'aspect':'media',
              'verify_token':'myVerifyToken',
              'callback_url':"${ctx.parameters()['samiUrlBase']}${ctx.cloudId()}/thirdpartynotifications",
            ])
            .withContent("", "multipart/form-data")
        ctx.debug("setup request" + req)
        return new Good(Option.apply(req))
    }

    @Override
    def Or<Boolean, Failure> onSetupResponse(Context ctx, RequestDef req, Response res) {
        ctx.debug("setup response" + res)
        return new Good(Option.apply(true))
    }

    @Override
    def Or<List<RequestDef>, Failure> subscribe(Context ctx, DeviceInfo info) {
        return new Good([new RequestDef(PROFILE_URL)])
    }

    @Override
    def Or<Option<DeviceInfo>,Failure> onSubscribeResponse(Context ctx, RequestDef req,  DeviceInfo info, Response res) {
        if (req.url() == PROFILE_URL){
            if (res.status() == HTTP_OK){
                def json = slurper.parseText(res.content)
                def userId = json.data.id.toString()
                ctx.debug("Retrieving userId : " + userId)
                return new Good(Option.apply(info.withExtId(userId)))
            }
            else {
                return new Bad(Failure("Impossible to recover user id from Instagram API."))
            }
        }
        else{
            return new Good(Option.apply(null))
        }
    }

    def String generateSignature(RequestDef req, String secret) {
        def sortedParams = req.queryParams().sort{ it.getKey() }
        if (req.url().size() < API_URL.size()){
            throw new Exception("Impossible to fetch endpoint of url for req: " + req.url())
        }
        def endPoint = req.url().substring(API_URL.size())
        def dataToHash = sortedParams.inject(endPoint){result, k, v -> "$result|$k=$v"}
        return CryptographicHelper.HmacSha256(secret, dataToHash)
    }

    @Override
    def Or<RequestDef, Failure> signAndPrepare(Context ctx, RequestDef req, DeviceInfo info, Phase phase){
        switch (phase){
            case Phase.undef:
            case Phase.subscribe:
            case Phase.unsubscribe:
            case Phase.fetch:
            case Phase.refreshToken:
                def reqWithToken = req.addQueryParams(["access_token" : info.credentials().token()])
                def signHash = generateSignature(reqWithToken, ctx.clientSecret())
                return new Good(reqWithToken.addQueryParams(["sig" : signHash]))
                break
            default:
                super.signAndPrepare(ctx, req, info, phase)
        }
    }

    /**
     * Instagram is checking our callback : https://instagram.com/developer/realtime/
     * Here we could check the verify_token to be sure our application is the one who is effectively subscribing.
     * But this shared secret would be hardcoded here, and need to be used in the curl command to create the subscription.
     */
    def Or<NotificationResponse, Failure> answerToChallengeRequest(RequestDef req){
        def challenge = req.queryParams().get("hub.challenge")
        def verify_token = req.queryParams().get("hub.verify_token")
        if (challenge != null && verify_token != null)
            new Good(new NotificationResponse([], new Response(HTTP_OK, "text/plain", challenge)))
        else
            new Bad(Failure("An incomplete callback verification arrived (missing challenge or verify token in req $req)"))
    }

    @Override
    def Or<NotificationResponse, Failure> onNotification(Context ctx, RequestDef inReq) {
        if (inReq.method() == HttpMethod.Get && inReq.queryParams().get("hub.mode") == "subscribe"){
            answerToChallengeRequest(inReq)
        } else {
            def json = slurper.parseText(inReq.content)
            new Good(new NotificationResponse(json.collectMany { notificationContent ->
                if (notificationContent.object == "user" && notificationContent.object_id != null && notificationContent.time != null){
                    def externalUserId=notificationContent.object_id.toString()
                    def timestamp = (notificationContent.time as long)
                    def reqToDo=[new RequestDef(ENDPOINT_URL).withQueryParams(["min_timestamp" : (timestamp - REQUEST_TIME_RANGE).toString(), "max_timestamp" : (timestamp + 1).toString()])]
                    [new ThirdPartyNotification(new ByExternalDeviceId(externalUserId), reqToDo, Empty.list())]
                }
                else {
                    ctx.debug("Invalid callback request content : " + notificationContent + ". (callbackRequest = " + inReq + ")")
                    []
                }
            }))
        }
    }

    def long retrieveTimestampFromRequest(RequestDef req, jsData) {
        def ts = (jsData.created_time)? (jsData.created_time as long) : ((req.queryParams().get("min_timestamp") as long) + REQUEST_TIME_RANGE)
        return ts * 1000
    }

    def denull(obj) {
        if(obj instanceof java.util.Map) {
            obj.collectEntries {k, v ->
                (v != null)? [(k): denull(v)] : [:]
            }
        } else if(obj instanceof java.util.Collection) {
            obj.collect { denull(it) }.findAll { it != null }
        } else {
            obj
        }
    }

    @Override
    def Or<List<Event>, Failure> onFetchResponse(Context ctx, RequestDef req, DeviceInfo info, Response res) {
        switch(res.status) {
            case HTTP_OK:
                def content = res.content.trim()
                if (content == "") {
                    ctx.debug("ignore empty content for response : $res")
                    return new Good(Empty.list())
                } else if (res.contentType.startsWith("application/json")) {
                    def json = slurper.parseText(content)
                    def events = json.data.collect { jsData ->
                        def js = denull(jsData)
                        new Event(retrieveTimestampFromRequest(req, js), JsonOutput.toJson(js))
                    }
                    return new Good(events)
                }
                return new Bad(new Failure("unsupported response ${res} ... ${res.contentType} .. ${res.contentType.startsWith("application/json")}"))
            default:
                return new Bad(new Failure("http status : ${res.status} is not OK (${HTTP_OK})"))
        }
    }
}
