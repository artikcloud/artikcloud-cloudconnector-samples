// Sample CloudConnector, that can be used as a boostrap to write a new CloudConnector.
// Every code is commented, because everything is optional.
// The class can be named as you want no additional import allowed
// See the javadoc/scaladoc of com.samsung.sami.cloudconnector.api.CloudConnector
package io.samsungsami.underarmour

import com.samsung.sami.cloudconnector.api_v1.*
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.joda.time.*
import org.scalactic.*
import scala.Option

import static java.net.HttpURLConnection.*

//@CompileStatic
class MyCloudConnector extends CloudConnector {
    static final CT_JSON = 'application/json'

    JsonSlurper slurper = new JsonSlurper()

    @Override
    def Or<RequestDef, Failure> signAndPrepare(Context ctx, RequestDef req, DeviceInfo info, Phase phase){
        switch (phase){
            case Phase.undef:
            case Phase.subscribe:
            case Phase.unsubscribe:
            case Phase.fetch:
            case Phase.refreshToken:
                new Good(req.addHeaders(["Api-Key": ctx.clientId(), "Authorization": "Bearer " + info.credentials.token]))
                break
            default:
                super.signAndPrepare(ctx, req, info, phase)
        }
    }

    @Override
    def Or<List<RequestDef>, Failure> subscribe(Context ctx, DeviceInfo info) {
        def hookJs = ["callback_url": ctx.parameters()['notificationCallback'] ,
                      "shared_secret": ctx.parameters()['sharedSecret'] ,
                      "subscription_type": "application.actigraphies"]
        def hookReq = new RequestDef("${ctx.parameters()['endPointUrl']}/v7.1/webhook/")
                .withMethod(HttpMethod.Post)
                .withContent(JsonOutput.toJson(hookJs), "application/json")
        def userReq = new RequestDef("${ctx.parameters()['endPointUrl']}/v7.1/user/self")
                .withMethod(HttpMethod.Get)
        return new Good([hookReq, userReq])
    }
/*
    @Override
    def Or<List<RequestDef>, Failure> unsubscribe(Context ctx, DeviceInfo info) {
        def userDataJson = slurper.parseText(info.userData().getOrElse("{}"))
        def webhookId = userDataJson?.subscriptionId ?: ""
        if (webhookId != "") {
            def disableHookJs = ["status": "disabled"]
            def hookReq = new RequestDef("${ctx.parameters()['endPointUrl']}/v7.1/webhook/$webhookId")
                    .withMethod(HttpMethod.Put)
                    .withContent(JsonOutput.toJson(disableHookJs), "application/json")
            new Good([hookReq])
        }
        else {
            new Bad(new Failure("missing subscriptionId in userData"))
        }
    }
*/
    @Override
    def Or<Option<DeviceInfo>,Failure> onSubscribeResponse(Context ctx, RequestDef req,  DeviceInfo info, Response res) {
        //{"status":"active","last_updated":"2015-12-11T10:25:36.326824+00:00","created":"2015-12-11T10:25:36.326801+00:00","subscription_type":"application.actigraphies","last_degraded":null,"client_id":"y48m32964zbng6cbcht79fx6fgyqdj25","shared_secret":"SAMI shared $ecret","_links":{"self":[{"href":"\/v7.1\/webhook\/11421\/","id":"11421"}],"documentation":[{"href":"https:\/\/developer.underarmour.com\/docs\/v71_Webhook"}]},"callback_url":"https:\/\/test0.alchim31.net:9083\/cloudconnectors\/0000\/thirdpartynotifications","id":11421}
        switch (res.status) {
            case HTTP_OK:
                def json = slurper.parseText(res.content())
                def id = json?.id
                if (req.method() == HttpMethod.Post) {
                    def userDataJson = slurper.parseText(info.userData().getOrElse("{}"))
                    userDataJson.put("subscriptionId", id)
                    new Good(Option.apply(info.withUserData(JsonOutput.toJson(userDataJson))))
                } else {
                    new Good(Option.apply(info.withExtId(String.valueOf(id))))
                }
                break
            default:
                new Good(Option.apply(null))
        }
    }

    @Override
    Or<NotificationResponse, Failure> onNotification(Context ctx, RequestDef inReq) {
        def json = slurper.parseText(inReq.content())
        def type = json?.type ?: [""]
        switch (type[0]){
            case "application.actigraphies":
                def thirdPardyNotifications = json.collect { notification ->
                    def userId = notification._links.user[0].id
                    def deviceSelector = new ByExternalDeviceId(userId)
                    def urlEnd = notification._links.actigraphy[0].href
                    println(new RequestDef("${ctx.parameters()['endPointUrl']}${urlEnd}").addQueryParams(['underArmourTs':notification.ts]))
                    new ThirdPartyNotification(deviceSelector, [new RequestDef("${ctx.parameters()['endPointUrl']}${urlEnd}").addQueryParams(['underArmourTs':notification.ts])])
                }
                new Good(new NotificationResponse(thirdPardyNotifications))
                break
            default:
                new Good(new NotificationResponse([]))
        }

    }
    
    @Override
    Or<List<Event>, Failure> onFetchResponse(Context ctx, RequestDef req, DeviceInfo info, Response res) {
        switch (res.status) {
            case HTTP_OK:
                def content = res.content.trim()
                ctx.debug(content)
                if (content == '' || content == 'OK') {
                    ctx.debug("ignore response valid respond: '${res.content}'")
                    return new Good([])
                } else if (res.contentType.startsWith(CT_JSON)) {
                    def ts = new DateTime(req.queryParams()['underArmourTs']).getMillis()
                    def json = slurper.parseText(content)
                    def actigraphiesJson = json._embedded?.actigraphies ?: []
                    def events = actigraphiesJson?.collectMany{ actigraphie ->
                        extractMetrics(actigraphie, ts) +
                                extractAggregates(actigraphie, ts) +
                                extractWorkouts(actigraphie, ts)
                    }
                    return new Good(events)
                }
                return new Bad(new Failure("unsupported response ${res} ... ${res.contentType} .. ${res.contentType.startsWith(CT_JSON)}"))
            default:
                return new Bad(new Failure("http status : ${res.status} is not OK (${HTTP_OK})"))
        }
    }

    private def extractMetrics(jsonNode, long ts) {
        def type = "metric"
        def steps = jsonNode?.metrics?.steps ?: []
        def stepEvents = steps.collectMany { stepsInfo ->
            def startTime = new DateTime(stepsInfo.start_datetime_utc)
            def endTime = new DateTime(stepsInfo.end_datetime_utc)
            stepsInfo.time_series.epoch_values.collect { tsAndCount ->
                if (tsAndCount.size == 2) {
                    new Event(ts, '''{"steps":{''' + writeStep(type, tsAndCount[1], startTime, endTime) + ''', "timestamp":''' + Math.round(tsAndCount[0] / 1000) + "}}")
                }
            }
        }
        def sleeps = jsonNode?.metrics?.sleep ?: []
        def sleepEvents = sleeps.collectMany { sleepsInfo ->
            def startTime = new DateTime(sleepsInfo.start_datetime_utc)
            def endTime = new DateTime(sleepsInfo.end_datetime_utc)
            def totalSleep = sleepsInfo?.sum
            def lightSleep = sleepsInfo?.details?.light_sleep?.sum
            def deepSleep = sleepsInfo?.details?.deep_sleep?.sum
            def awake = sleepsInfo?.details?.awake?.sum
            [new Event(ts, '''{"sleep":{''' + writeSleep(type, awake, totalSleep, lightSleep, deepSleep, startTime, endTime) + "}}")]
        }
        stepEvents + sleepEvents
    }

    private def extractAggregates(jsonNode, long ts) {
        def type = "aggregates"
        def startTime = new DateTime(jsonNode.start_datetime_utc)
        def endTime = new DateTime(jsonNode.end_datetime_utc)
        def heartRateResting = jsonNode?.aggregates?.heart_rate_resting?.latest ?: -42
        if (heartRateResting != -42)
            [new Event(ts, '''{"heartrate":{''' + writeHeartRate(type, heartRateResting, startTime, endTime) + "}}")]
        else
            []
    }

    private def extractWorkouts(jsonNode, long ts) {
        def type = "workout"
        def workouts = jsonNode?.workouts ?: []
        workouts.collectMany { workoutsInfo ->
            def startTime = new DateTime(workoutsInfo.start_datetime_utc)
            def endTime = new DateTime(workoutsInfo.end_datetime_utc)
            def steps = workoutsInfo?.aggregates?.details?.steps?.sum ?: 0
            if (steps != 0){
                [new Event(ts, '''{"steps":{''' + writeStep(type, steps , startTime, endTime) + "}}")]
            } else {
                []
            }
        }
    }


    // write functions to write Js String

    private def writeSleep(String type, Integer awake, Integer sum, Integer light, Integer deep, DateTime start, DateTime end){
        writeType(type) + ''', "awake":''' + awake + ''', "totalSleep":''' + sum +
                ''', "lightSleep":''' + light + ''', "deepSleep":''' + deep +
                writeDates(start, end)
    }

    private def writeHeartRate(String type, Integer heartRateResting, DateTime start, DateTime end){
        writeType(type) + ''', "resting":''' + heartRateResting + ", " + writeDates(start, end)
    }

    private def writeStep(String type, Integer count, DateTime start, DateTime end){
        writeType(type) + ", " + writeCount(count) + ", " + writeDates(start, end)
    }

    private def writeType(String type){
        '''"type":"''' + type + '''"'''
    }

    private def writeCount(Integer count){
        '''"count":''' + count
    }

    private def writeDates(DateTime start, DateTime end){
        '''"startDate":''' + start.getMillis() + ''', "endDate":''' + end.getMillis()
    }
}
