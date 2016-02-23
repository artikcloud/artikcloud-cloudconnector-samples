// Sample CloudConnector, that can be used as a boostrap to write a new CloudConnector.
// Every code is commented, because everything is optional.
// The class can be named as you want no additional import allowed
// See the javadoc/scaladoc of com.samsung.sami.cloudconnector.api.CloudConnector
package io.samsungsami.withings

import com.samsung.sami.cloudconnector.api_v1.*
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.format.DateTimeFormat
import org.joda.time.*
import org.scalactic.*
import scala.Option

import static java.net.HttpURLConnection.*

//@CompileStatic
class MyCloudConnector extends CloudConnector {
    def PUBSUB_ENDPOINT_URL = "https://wbsapi.withings.net/notify"
    def API_ENDPOINT = "https://wbsapi.withings.net/"
    def MEASURE_ENDPOINT = API_ENDPOINT + "measure"
    def ACTIVITY_ENDPOINT = API_ENDPOINT + "v2/measure"
    def SLEEP_ENDPOINT = API_ENDPOINT + "v2/sleep"
    def SUBSCRIBE_ACTION = "subscribe"

    def DEVICES_APPLI_ID = [
            1 : "Body Scale",
            4 : "Blood pressure monitor",
            16 : "Withings pulse",
            44 : "Sleep monitor"
    ]

    static final mdateFormat = DateTimeFormat.forPattern("yyyy-MM-dd").withZoneUTC()
    JsonSlurper slurper = new JsonSlurper()
    @Override
    def Or<RequestDef, Failure> signAndPrepare(Context ctx, RequestDef req, DeviceInfo info, Phase phase){
        if (info.extId().isEmpty())
            return new Bad(new Failure("Withings need an user id to send a request to Third party cloud."))
        return new Good(OauthHelper.signQueryParams(
                req.addQueryParams(["userid" : info.extId().get()]),
                ctx.clientId(),
                ctx.clientSecret(),
                info.credentials().token(),
                info.credentials().secret()))
    }

    @Override
    def Or<List<RequestDef>, Failure> subscribe(Context ctx, DeviceInfo info) {
        def callbackUrl = { int appliId -> ctx.parameters().get("notificationBaseUrl") + ctx.cloudId() + "/thirdpartynotifications?samiDeviceId=" + info.did() + "&appliId=" + appliId }
        def formerCallbackUrl = { int appliId -> ctx.parameters().get("formerUrl") + "?samiDeviceId=" + info.did() + "&appliId=" + appliId }

        def req = {int appliId, String name -> new RequestDef(PUBSUB_ENDPOINT_URL).withQueryParams(subscriptionParameters(SUBSCRIBE_ACTION, callbackUrl(appliId), appliId, name)) }
        def formerReq = { int appliId, String name -> new RequestDef(PUBSUB_ENDPOINT_URL).withQueryParams(subscriptionParameters("revoke", formerCallbackUrl(appliId), appliId, name)) }

        def subReq = DEVICES_APPLI_ID.collect({k,v -> formerReq(k,v)}) + DEVICES_APPLI_ID.collect({k,v -> req(k,v)})
        ctx.debug("Suscribing to notification : " + subReq)
        return new Good(subReq)
    }

    @Override
    Or<List<RequestDef>, Failure> unsubscribe(Context ctx, DeviceInfo info) {
        def callbackUrl = {int appliId -> ctx.parameters().get("notificationBaseUrl") + ctx.cloudId() + "/thirdpartynotifications?samiDeviceId=" + info.did() + "&appliId=" + appliId}
        def req = {int appliId, String name -> new RequestDef(PUBSUB_ENDPOINT_URL).withQueryParams(subscriptionParameters("revoke", callbackUrl(appliId), appliId, name)) }
        def unSubReq = DEVICES_APPLI_ID.collect({k,v -> req(k,v)})
        ctx.debug("Unsuscribing to notification : " + unSubReq)
        return new Good(unSubReq)
    }


    @Override
    def Or<Option<DeviceInfo>,Failure> onSubscribeResponse(Context ctx, RequestDef req,  DeviceInfo info, Response res) {
        def json = slurper.parseText(res.content().trim())
        def status = json.status
        def action = req.queryParams()["action"]
        if ((status == 0) || (action != SUBSCRIBE_ACTION)){
            return new Good(Option.apply(null))
        }
        else {
            return new Bad(new Failure("can't subscribe json status : ${json.status} is not OK (200)"))
        }
    }

    @Override
    def Or<NotificationResponse, Failure> onNotification(Context ctx, RequestDef req) {
        def did = req.queryParams().get("samiDeviceId")
        if (did == null) {
            ctx.debug("Bad notification (where is did in following req : ) " + req)
            return new Bad(new Failure("Impossible to recover device id from token request."))
        }
        def appliId = req.queryParams().get("appliId")
        if (appliId == null) {
            ctx.debug("Bad notification (where is appliId in following req ? : ) " + req)
            return new Bad(new Failure("Impossible to recover appliId from token request."))
        }
        if (!(DEVICES_APPLI_ID.keySet().contains(appliId.toInteger()))){
            ctx.debug("Bad notification (appliId " + appliId + " not in " + DEVICES_APPLI_ID.keySet().toString() +") : " + req)
            return new Bad(new Failure("Invalid appli id : " + appliId))
        }
        def content = req.content.trim()
        if (content == "AnyContentAsEmpty") { //resub notif
            return new Good(new NotificationResponse([]))
        }


        def json = slurper.parseText(content)
        def startDate = json.startdate
        def endDate = json.enddate
        if ((appliId.toInteger() != 16 ) && ((startDate == null) || (endDate == null))) {
            ctx.debug("Bad notification (appliId " + appliId + " not in " + DEVICES_APPLI_ID.keySet().toString() +") : " + req)
            return new Bad(new Failure("Invalid startdate or enddate : startDate=" + startDate + " endDate=" + endDate))
        }
        def requestsToDo
        switch(appliId.toInteger()){
            case 1:
                requestsToDo = [
                        new RequestDef(MEASURE_ENDPOINT).
                                withQueryParams(["action" : "getmeas", "startdate" : startDate[0], "enddate" : endDate[0]])]
                break
            case 4:
                requestsToDo = [
                        new RequestDef(MEASURE_ENDPOINT).
                                withQueryParams(["action" : "getmeas", "startdate" : startDate[0], "enddate" : endDate[0]])]

                break
            case 16: //For Pulse, only query param = "yyyy-MM-dd" is defined.
                def dateStr = req.queryParams().get("date")
                if ((dateStr == null) && (json.date != null)) {
                    dateStr = json.date[0]
                }
                if (dateStr != null) {
                    startDate = (DateTime.parse(dateStr, mdateFormat).getMillis() / 1000).toString()
                    endDate = startDate
                    requestsToDo = [
                            new RequestDef(ACTIVITY_ENDPOINT).
                                    withQueryParams(["action" : "getactivity", "date" : dateStr]),
                            new RequestDef(MEASURE_ENDPOINT).
                                    withQueryParams(["action" : "getmeas", "startdate" : startDate, "enddate" : endDate])]
                } else {
                    if ((startDate == null) || (endDate == null)) {
                        return new Bad(new Failure("Invalid startdate or enddate : startDate=" + startDate + " endDate=" + endDate + " from " + req))
                    }else {
                        requestsToDo = [
                                new RequestDef(MEASURE_ENDPOINT).
                                        withQueryParams(["action" : "getmeas", "startdate" : startDate[0], "enddate" : endDate[0]])]
                    }
                }
                break
            case 44:
                requestsToDo = [
                        new RequestDef(SLEEP_ENDPOINT).
                                withQueryParams(["action" : "get", "startdate" : startDate[0], "enddate" : endDate[0]]),
                        new RequestDef(MEASURE_ENDPOINT).
                                withQueryParams(["action" : "getmeas", "startdate" : startDate[0], "enddate" : endDate[0]])
                ]
                break
            default:
                break
        }
        if (requestsToDo == null){
            ctx.debug("Bad notification, impossible to build query from :" + req)
            return new Bad(new Failure("impossible to build query for app Id = " + appliId))
        }
        new Good(new NotificationResponse([new ThirdPartyNotification(new BySamiDeviceId(did), requestsToDo)]))
    }

    @Override
    def Or<List<Event>, Failure> onFetchResponse(Context ctx, RequestDef req, DeviceInfo info, Response res) {
        switch(res.status) {
            case HTTP_OK:
                def content = res.content().trim()
                if (content == "" || content == "OK") {
                    ctx.debug("Ignore response valid respond: '${res.content()}'")
                    return new Good(Empty.list())
                } else {
                    def json = slurper.parseText(content)
                    if (json.status == 0) {
                        def result = []
                        switch (req.url()) {
                            case MEASURE_ENDPOINT:
                                result = json.body.measuregrps.collect { js ->
                                    new Event(Long.valueOf(js.date) * 1000L, contentOfEvent(js, "measure/getmeas"))
                                }
                                break
                            case ACTIVITY_ENDPOINT:
                                result = json.body.activities.collect { js ->
                                    new Event(getGetTsFromDate(js), contentOfEvent(js, "measure/getactivity"))
                                }
                                if (result == []){
                                    result = [new Event(getGetTsFromDate(json.body), contentOfEvent(json.body, "measure/getactivity"))]
                                }
                                break
                            case SLEEP_ENDPOINT:
                                result = json.body.series.collect { js ->
                                    new Event(Long.valueOf(js.startdate) * 1000L, contentOfEvent(js, "sleep/get"))
                                }
                                break
                            default:
                                return new Bad(new Failure("Impossible to parse action from request of origin"))
                                break
                        }
                        return new Good(result)
                    }
                    return new Bad(new Failure("json status : ${json.status} is not OK (200)"))
                }
                break
            default:
                return new Bad(new Failure("http status : ${res.status()} is not OK (${HTTP_OK})"))
        }
    }



    def getGetTsFromDate(Object js){
        return DateTime.parse(js.date, mdateFormat.withZone(DateTimeZone.forID(js.timezone))).getMillis()
    }

    def contentOfEvent(Object js, String measureName) {
        def builder = new groovy.json.JsonBuilder()
        JsonOutput.toJson(builder {
            measuregrp js
            category measureName
        })
    }


    def subscriptionParameters = { String action, String url, int appliId, String name ->
        [
                "action"     : action,
                "appli"      : String.valueOf(appliId),
                "callbackurl": url,
                "comment"    : "Notifications to " + name + " for Samsung SAMI Platform"]
    }
}
