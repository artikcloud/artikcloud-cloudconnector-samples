
package io.samsungsami.fitbit

import scala.Option

import static java.net.HttpURLConnection.*

import org.scalactic.*
import org.joda.time.format.DateTimeFormat
import org.joda.time.*
import java.nio.charset.StandardCharsets
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import com.samsung.sami.cloudconnector.api_v1.*
import org.apache.commons.codec.binary.Base64;

//TODO implement unsubscription (it's a workaround for https://samihub.atlassian.net/browse/SAMI-3607)
class MyCloudConnector extends CloudConnector {
    JsonSlurper slurper = new JsonSlurper()
    static final mdateFormat = DateTimeFormat.forPattern("yyyy-MM-dd").withZoneUTC()

    def isSameDay(DateTime d1, DateTime d2) {
        (d1.year== d2.year) && (d1.dayOfYear ==  d2.dayOfYear)
    }

    def getTimestampOfTheEndOfTheDay(DateTime date) {
        date.minusMillis((date.millisOfDay().get() + 1 )).plusDays(1)
    }

    def baseUrl(Context ctx) {
        ctx.parameters().get("baseUrl")
    }

    def pubsubUrl(Context ctx, String did) {
        baseUrl(ctx) + "apiSubscriptions/sami-" + did + ".json"
    }

    def profileUrl(Context ctx) {
        baseUrl(ctx) + "profile.json"
    }

    /**
     * Since we recover the summary data of the day, we want to have a meaningful timestamp from source:
     * If the date is not today : the day is finished. We set the source timestamp to the last second of this past day.
     * If the date is today : the day is not finished, data can continue to evolve for the day, we set the timestamp to now()
     */
    def getTimestampFromDate(DateTime date, DateTimeZone dtz = DateTimeZone.UTC) {
        def now = new DateTime(dtz).toDateTime(dtz)
        def returnedDate = isSameDay(date, now)? now : getTimestampOfTheEndOfTheDay(date)
        returnedDate.getMillis()
    }


    @Override
    def Or<List<RequestDef>, Failure> subscribe(Context ctx, DeviceInfo info) {
        new Good([
                new RequestDef(pubsubUrl(ctx, info.did())).
                        withContent("", "application/x-www-form-urlencoded; charset=UTF-8").
                        withMethod(HttpMethod.Post),
                new RequestDef(profileUrl(ctx))
        ])
    }

    @Override
    Or<List<RequestDef>, Failure> unsubscribe(Context ctx, DeviceInfo info) {
        new Good([new RequestDef(pubsubUrl(ctx, info.did())).withMethod(HttpMethod.Delete)])
    }

    @Override
    def Or<Option<DeviceInfo>,Failure> onSubscribeResponse(Context ctx, RequestDef req,  DeviceInfo info, Response res) {
        if (req.url() == profileUrl(ctx)){
            if (res.status() == HTTP_OK){
                def json = slurper.parseText(res.content)
                def tzStr = json.user.timezone.toString()
                try {
                    DateTimeZone.forID(tzStr)
                    return new Good(Option.apply(info.withUserData(tzStr)))
                } catch (IllegalArgumentException e){
                    return new Bad(Failure("A timezone has be recover from user profile :" + tzStr + ", but this is not a valid DateTimeZone id"))
                }
            } else {
                return new Bad(Failure("Impossible to get user profile information from fitbit api."))
            }
        } else {
            return new Good(Option.apply(null))
        }
    }

    /**
     * The Authorization header must be set to Basic followed by a space,
     * then the Base64 encoded string of your application's client id and secret concatenated with a colon.
     * For example, the Base64 encoded string, Y2xpZW50X2lkOmNsaWVudCBzZWNyZXQ=, is decoded as "client_id:client secret".
     */
    def computeAuthHeader(String clientId, String clientSecret) {
        def secret = (clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8)
        return new String(Base64.encodeBase64(secret), StandardCharsets.UTF_8)
    }

    @Override
    def Or<RequestDef, Failure> signAndPrepare(Context ctx, RequestDef req, DeviceInfo info, Phase phase){
        switch (phase) {
            case Phase.refreshToken:
            case Phase.getOauth2Token:
                def authHeader = computeAuthHeader(ctx.clientId(), ctx.clientSecret())
                return new Good(req.withHeaders(["Authorization": "Basic " + authHeader]))
            case Phase.undef:
            case Phase.subscribe:
            case Phase.unsubscribe:
            case Phase.fetch:
                return new Good(req.addHeaders(["Authorization": "Bearer " + info.credentials.token]))
                break
            default:
                super.signAndPrepare(ctx, req, info, phase)
        }
    }

    def apiEndpoint(Context ctx, String collectionType, String date) {
        def endpoint
        switch (collectionType) {
            case "activities":
                endpoint = "activities"
                break
            case "sleep":
                endpoint = "sleep"
                break
            case "body":
                endpoint = "body"
                break
            case "foods":
                endpoint = "foods/log"
                break
            default:
                throw new Exception("Invalid collectionType" + collectionType)
        }
        def urlEndpoints = [baseUrl(ctx) + endpoint + "/date/" + date + ".json"]
        if (collectionType == "activities"){
            def heartRateEndpoint = (baseUrl(ctx) + "activities/heart/date/" + date  + "/1d.json")
            return urlEndpoints + heartRateEndpoint
        }
        return urlEndpoints
    }

    def createNotificationFromResult(Context ctx, java.lang.Object e) {
        String did = e.subscriptionId.substring("sami-".length());
        String collectionType = e.collectionType;
        String date = e.date;
        def requestsToDo = apiEndpoint(ctx, collectionType, date).collect {ep -> new RequestDef(ep)}
        new ThirdPartyNotification(new BySamiDeviceId(did), requestsToDo)
    }

    def Or<NotificationResponse, Failure> answerToChallengeRequest(RequestDef req, String verifyCode){
        if (!(req.queryParams().containsKey("verify")))
            return new Bad(Failure("An incomplete callback verification arrived : " + req))

        if (req.queryParams().get("verify") == verifyCode)
            return new Good(new NotificationResponse([], new Response(HTTP_NO_CONTENT, "text/plain", "")))

        return new Good(new NotificationResponse([], new Response(HTTP_NOT_FOUND, "text/plain", "")))
    }

    @Override
    def Or<NotificationResponse, Failure> onNotification(Context ctx, RequestDef inReq) {
        if(inReq.queryParams().containsKey("verify"))
            return answerToChallengeRequest(inReq, ctx.parameters().get("endpointVerificationCode"))
        if (inReq.contentType().startsWith("application/json")) {
            def json = slurper.parseText(inReq.content)
            return new Good(new NotificationResponse(json.collect { e -> createNotificationFromResult(ctx, e) }))
        } else if (inReq.contentType().startsWith("multipart/form-data")) {
            ctx.debug("content :" + inReq.content())
            def fileNames = ctx.requestDefTools().listFilesFromMultipartFormData(inReq)
            def result = fileNames.collect { fileName ->
                def json = slurper.parseText(ctx.requestDefTools().readFileFromMultipartFormData(inReq, fileName).get())
                json.collect { e -> createNotificationFromResult(ctx, e) }
            }.flatten()
            return new Good(new NotificationResponse(result))
        } else {
            return new Bad(new Failure("Bad content type for incoming notification : " + inReq.contentType()))
        }
    }

    @Override
    def Or<List<Event>, Failure> onFetchResponse(Context ctx, RequestDef req, DeviceInfo info, Response res) {
        switch(res.status) {
            case HTTP_OK:
                def tz
                def dateStr = req.url().split("/").reverse().head().substring(0, 10) //return YYYY-MM-DD
                if (info.userData().isEmpty()) {
                    ctx.debug("No user data has been found: using UTC timezone instead of user defined timezone.")
                    tz = DateTimeZone.UTC
                } else {
                    def tzId = info.userData().get()
                    tz = DateTimeZone.forID(tzId)
                }
                def ts = getTimestampFromDate(DateTime.parse(dateStr, mdateFormat.withZone(tz)), tz)

                def content = res.content().trim()
                if (content == "" || content == "OK") {
                    ctx.debug("Ignore response valid respond: '${res.content()}'")
                    return new Good(Empty.list())
                } else if (res.contentType().startsWith("application/json")) {
                    def json = slurper.parseText(content)
                    return new Good([new Event(ts, JsonOutput.toJson(json))])
                }
                return new Bad(new Failure("unsupported response ${res} ... ${res.contentType()}"))
                break
            default:
                return new Bad(new Failure("http status : ${res.status()} is not OK (${HTTP_OK})"))
        }
    }
}