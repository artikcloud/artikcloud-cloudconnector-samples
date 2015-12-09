// Sample CloudConnector, that can be used as a boostrap to write a new CloudConnector.
// Every code is commented, because everything is optional.
// The class can be named as you want no additional import allowed
// See the javadoc/scaladoc of com.samsung.sami.cloudconnector.api_v1.CloudConnector
package com.sample

import static java.net.HttpURLConnection.*

import org.scalactic.*
import org.joda.time.format.DateTimeFormat
import org.joda.time.*
import groovy.transform.CompileStatic
import groovy.transform.ToString
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import com.samsung.sami.cloudconnector.api_v1.*
import org.joda.time.format.ISODateTimeFormat

//@CompileStatic
class MyCloudConnector extends CloudConnector {
    static mdateFormat = DateTimeFormat.forPattern('yyyy-MM-dd HH:mm:ss').withZoneUTC()
    static final CT_JSON = 'application/json'

    JsonSlurper slurper = new JsonSlurper()

    @Override
    Or<RequestDef, Failure> signAndPrepare(Context ctx, RequestDef req, DeviceInfo info, Phase phase) {
        ctx.debug('phase: ' + phase)
        //if (phase == Phase.refreshToken) {
        //   //TODO change some params
        //}
        new Good(req.addHeaders(['Authorization':'Bearer ' + info.credentials.token]))
    }

    // -----------------------------------------
    // SUBSCRIPTION
    // -----------------------------------------

    @Override
    Or<List<RequestDef>, Failure> subscribe(Context ctx, DeviceInfo info) {
        new Good([new RequestDef("${ctx.parameters().endpoint}/subscribe").withMethod(HttpMethod.Post).withContent('subscriptionId=' + info.did(), 'text/plain')])
    }

    // @Override
    // Or<Option<DeviceInfo>,Failure> onSubscribeResponse(Context ctx, RequestDef req,  DeviceInfo info, Response res) {
    //   def json = slurper.parseText(res.content)
    //   new Good(Option.apply(info.withExtId(json.userId)))
    // }

    @Override
    Or<List<RequestDef>, Failure> unsubscribe(Context ctx, DeviceInfo info) {
        new Good([new RequestDef("${ctx.parameters().endpoint}/unsubscribe").withMethod(HttpMethod.Get)])
    }

    // @Override
    // Or<Option<DeviceInfo>,Failure> onSubscribeResponse(Context ctx, RequestDef req,  DeviceInfo info, Response res) {
    //   super.onUnsubscribeResponse(ctx, req, info, res)
    // }

    // -----------------------------------------
    // CALLBACK
    // -----------------------------------------
    @Override
    Or<NotificationResponse, Failure> onNotification(Context ctx, RequestDef inReq) {
        def did = inReq.headers()['notificationId']
        if (did == null) {
            ctx.debug('Bad notification (where is did in following req : ) ' + inReq)
            return new Bad(new Failure('Impossible to recover device id from token request.'))
        }
        def content = inReq.content()
        def json = slurper.parseText(content)

        def dataToFetch = json.messages.collect { e ->
            new RequestDef("${ctx.parameters().endpoint}/messages/${e}")
        }

        new Good(new NotificationResponse([new ThirdPartyNotification(new BySamiDeviceId(did), dataToFetch)]))
    }

    // @Override
  	// Or<RequestDef, Failure> fetch(Context ctx, RequestDef req, DeviceInfo info) {
  	// 	new Good(req.addQueryParams(['userId' : info.extId().getOrElse(null)]))
  	// }

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
                    def json = slurper.parseText(content)
                    def ts = (json.datetime) ? DateTime.parse(json.datetime, mdateFormat).millis : ctx.now()
                    //return new Good([new Event(ts, JsonOutput.toJson(slurper.parseText(json.message)))])
                    return new Good([new Event(ts, json.message)])
                }
                return new Bad(new Failure("unsupported response ${res} ... ${res.contentType} .. ${res.contentType.startsWith(CT_JSON)}"))
            default:
                return new Bad(new Failure("http status : ${res.status} is not OK (${HTTP_OK})"))
        }
    }

    // Or<Seq<Event>, Failure> onNotificationData(Context ctx, DeviceInfo info, String data) {
  	// 	new Bad(new Failure("unsupported: method onNotificationData should be implemented"))
    // }
}
