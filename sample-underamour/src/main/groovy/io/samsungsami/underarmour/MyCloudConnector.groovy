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
            default:
                new Good(req.addHeaders(["Api-Key": ctx.clientId(), "Authorization": "Bearer " + info.credentials.token]))
                //super.signAndPrepare(ctx, req, info, phase)
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
                          extractWorkouts(actigraphie, ts)
                    }
                    //JsonOutput.toJson
                    return new Good(events)
                }
                return new Bad(new Failure("unsupported response ${res} ... ${res.contentType} .. ${res.contentType.startsWith(CT_JSON)}"))
            default:
                return new Bad(new Failure("http status : ${res.status} is not OK (${HTTP_OK})"))
        }
    }

    private def extractMetrics(jsonNode, long ts) {
        def steps = jsonNode?.metrics?.steps ?: []
        steps.collectMany { stepsInfo ->
            def startTime = new DateTime(stepsInfo.start_datetime_utc)
            def endTime = new DateTime(stepsInfo.end_datetime_utc)
            stepsInfo.time_series.epoch_values.collect { tsAndCount ->
                if (tsAndCount.size == 2) {
                    new Event(ts, '''{"steps":{''' + writeStep("metric", tsAndCount[1], startTime, endTime) + ''', "timestamp":''' + tsAndCount[0] + "}}")
                }
            }
        }
    }

    private def extractWorkouts(jsonNode, long ts) {
        def workouts = jsonNode?.workouts ?: []
        workouts.collectMany { workoutsInfo ->
            def startTime = new DateTime(workoutsInfo.start_datetime_utc)
            def endTime = new DateTime(workoutsInfo.end_datetime_utc)
            def steps = workoutsInfo?.aggregates?.details?.steps?.sum ?: 0
            if (steps != 0){
                [new Event(ts, '''{"steps":{''' + writeStep("workout", steps , startTime, endTime) + "}}")]
            } else {
                []
            }
        }
    }

    private def writeStep(String type, Integer count, DateTime start, DateTime end){
        '''"type":"''' + type + '''", "count":''' + count +
                ''', "startDate":''' + start.getMillis() + ''', "endDate":''' + end.getMillis()
    }
}
