package cloudconnector

import scala.Option

import javax.management.Notification

import static java.net.HttpURLConnection.*

import org.scalactic.*
import org.joda.time.format.DateTimeFormat
import org.joda.time.*
import java.nio.charset.StandardCharsets
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import cloud.artik.cloudconnector.api_v1.*
import org.apache.commons.codec.binary.Base64;

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

    def pubsubUrl(Context ctx, String id) {
        baseUrl(ctx) + "apiSubscriptions/" + id + ".json"
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
            new RequestDef(pubsubUrl(ctx, "sami-" + info.did())).withMethod(HttpMethod.Delete),
            new RequestDef(pubsubUrl(ctx, "owner-" + info.extId().get())).
                withHeaders(["X-Fitbit-Subscriber-Id": ctx.parameters().get("SubscriberId")]).
                withContentType("application/x-www-form-urlencoded; charset=UTF-8").
                withMethod(HttpMethod.Post),
            new RequestDef(profileUrl(ctx))
        ])
    }

    @Override
    Or<List<RequestDef>, Failure> unsubscribeLast(Context ctx, DeviceInfo info) {
        new Good([
            new RequestDef(pubsubUrl(ctx, "owner-" + info.extId().get())).withMethod(HttpMethod.Delete),
            new RequestDef(pubsubUrl(ctx, "sami-" + info.did())).withMethod(HttpMethod.Delete),
        ])
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
                    return new Bad(new Failure("A timezone has be recover from user profile :" + tzStr + ", but this is not a valid DateTimeZone id"))
                }
            } else {
                return new Bad(new Failure("Impossible to get user profile information from fitbit, got status http status : ${res.status()}) with content: ${res.content()}"))
            }
        } else if (req.url().contains("apiSubscriptions") && req.method() == HttpMethod.Post) { //delete should not failed the subscription
            if(res.status() == HTTP_OK || res.status() == HTTP_CREATED) {
                //def extId = slurper.parseText(res.content).ownerId
                //return new Good(Option.apply(info.withExtId(extId)))
                return new Good(Option.apply(null))
            } else if(res.status() == HTTP_CONFLICT) {
                //Returned if the given user is already subscribed to this stream using a different subscription ID (already subscribed sami-device)
                //return new Bad(new Failure("Impossible to perform subscription because subscription already exists (conflict)"))

                // during the migration the subscription could failed with conflict until the first subscriber remove the active sami-xxx subscription
                return new Good(Option.apply(null))
            } else {
                // failed if error to setup subscription (bad credentials, ...)
                return new Bad(new Failure("Impossible to perform subscription error code = " + res.status()))
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

    private def removeScopeFromBody(RequestDef req){
        req.withBodyParams(req.bodyParams().findAll{ it.key != "scope" }).
                withQueryParams(req.queryParams().findAll{ it.key != "scope" })
    }

    @Override
    def Or<RequestDef, Failure> signAndPrepare(Context ctx, RequestDef req, DeviceInfo info, Phase phase){
        switch (phase) {
            case Phase.refreshToken:
                def authHeader = computeAuthHeader(ctx.clientId(), ctx.clientSecret())
                return new Good(removeScopeFromBody(req.withHeaders(["Authorization": "Basic " + authHeader])))
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
        if (e.subscriptionId.startsWith("sami-")) {
          //deprecated should not exists after migration to use of externalId
          String did = e.subscriptionId.substring("sami-".length());
          String collectionType = e.collectionType;
          String date = e.date;
          def requestsToDo = apiEndpoint(ctx, collectionType, date).collect {ep -> new RequestDef(ep).withHeaders(["remember_date": date])}
          new ThirdPartyNotification(new ByDid(did), requestsToDo)
        } else if (e.subscriptionId.startsWith("owner-")) {
          String extId = e.ownerId; // should be same value as in subscriptionId
          String collectionType = e.collectionType;
          String date = e.date;
          def requestsToDo = apiEndpoint(ctx, collectionType, date).collect {ep -> new RequestDef(ep).withHeaders(["remember_date": date])}
          new ThirdPartyNotification(new ByExtId(extId), requestsToDo)
        }
    }

    def Or<NotificationResponse, Failure> answerToChallengeRequest(RequestDef req, String verifyCode){
        if (!(req.queryParams().containsKey("verify")))
            return new Bad(new Failure("An incomplete callback verification arrived : " + req))

        if (req.queryParams().get("verify") == verifyCode)
            return new Good(new NotificationResponse([], new Response(HTTP_NO_CONTENT, "text/plain", "")))

        return new Good(new NotificationResponse([], new Response(HTTP_NOT_FOUND, "text/plain", "")))
    }

    def getLastDaysData(Context ctx) {
        def nbDayRetrieve = ctx.parameters().get("nbDayRetrieved").toInteger()
        def startDate = new Date(ctx.now()).minus(nbDayRetrieve)

        def daysToRetrieves = (0..nbDayRetrieve)
        ctx.debug(daysToRetrieves)
        def url = daysToRetrieves.collectMany{e ->
            def date = mdateFormat.print(startDate.plus(e).getTime())
            [
                    baseUrl(ctx) + "sleep" + "/date/" + date + ".json",
                    baseUrl(ctx) + "body" + "/date/" + date + ".json",
                    baseUrl(ctx) + "foods/log" + "/date/" + date + ".json",
                    baseUrl(ctx) + "activities/heart/date/" + date  + "/1d.json"
            ].collect{u -> new RequestDef(u).withHeaders(["remember_date": date])}
        }
        url
    }

    @Override
    def Or<NotificationResponse, Failure> onNotification(Context ctx, RequestDef req) {
        if (req.url().endsWith("postsubscription")) {
            def json = slurper.parseText(req.content())
            return new Good(new NotificationResponse([new ThirdPartyNotification(new ByDid(json.did), getLastDaysData(ctx))]))
        } else if(req.queryParams().containsKey("verify")) {
            return answerToChallengeRequest(req, ctx.parameters().get("endpointVerificationCode"))
        } else if (req.contentType().startsWith("application/json")) {
            def json = slurper.parseText(req.content)
            return new Good(new NotificationResponse(json.collect { e -> createNotificationFromResult(ctx, e) }))
        } else if (req.contentType().startsWith("multipart/form-data")) {
            ctx.debug("content :" + req.content())
            def fileNames = ctx.requestDefTools().listFilesFromMultipartFormData(req)
            def result = fileNames.collect { fileName ->
                def json = slurper.parseText(ctx.requestDefTools().readFileFromMultipartFormData(req, fileName).get())
                json.collect { e -> createNotificationFromResult(ctx, e) }
            }.flatten()
            return new Good(new NotificationResponse(result))
        } else {
            return new Bad(new Failure("Bad content type for incoming notification : " + req.contentType()))
        }
    }

    @Override
    def Or<List<Event>, Failure> onFetchResponse(Context ctx, RequestDef req, DeviceInfo info, Response res) {
        switch(res.status) {
            case HTTP_OK:
                def tz
                if (info.userData().isEmpty()) {
                    ctx.debug("No user data has been found: using UTC timezone instead of user defined timezone.")
                    tz = DateTimeZone.UTC
                } else {
                    def tzId = info.userData().get()
                    tz = DateTimeZone.forID(tzId)
                }
                def dateStr = req.headers().get("remember_date")
                def ts = getTimestampFromDate(DateTime.parse(dateStr, mdateFormat.withZone(tz)), tz)

                def content = res.content().trim()
                if (content == "" || content == "OK") {
                    ctx.debug("Ignore response valid respond: '${res.content()}'")
                    return new Good(Empty.list())
                } else if (res.contentType().startsWith("application/json")) {
                    def json = slurper.parseText(content)
                    // filter json to remove 'minuteData' (too big)
                    if (json?.sleep != null) {
                        json.sleep = json.sleep.collect{ it.remove('minuteData'); it}
                    }
                    return new Good([new Event(ts, JsonOutput.toJson(json))])
                }
                return new Bad(new Failure("unsupported response ${res} ... ${res.contentType()}"))
                break
            default:
                return new Bad(new Failure("[${info.did}] onFetchResponse got status http status : ${res.status()}) with content: ${res.content()}"))
        }
    }
}
