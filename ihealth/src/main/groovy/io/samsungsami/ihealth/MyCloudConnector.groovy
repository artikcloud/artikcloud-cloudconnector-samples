// Sample CloudConnector, that can be used as a boostrap to write a new CloudConnector.
// Every code is commented, because everything is optional.
// The class can be named as you want no additional import allowed
// See the javadoc/scaladoc of cloud.artik.cloudconnector.api.CloudConnector
package io.samsungsami.ihealth

import cloud.artik.cloudconnector.api_v1.*
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import org.joda.time.format.*
import org.joda.time.*
import org.scalactic.*

import static java.net.HttpURLConnection.*

@CompileStatic
class MyCloudConnector extends CloudConnector {
    static final DateTimeFormatter mdateFormat = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss").withZoneUTC()
    static final String endpoint = "https://api.ihealthlabs.com:8443/OpenApiV2"

    def JsonSlurper slurper = new JsonSlurper()

    @Override
    def Or<RequestDef, Failure> signAndPrepare(Context ctx, RequestDef req, DeviceInfo info, Phase phase){
        Map params = [:]
        params.putAll(req.queryParams())
        switch (phase) {
            case Phase.refreshToken:
                params.remove("grant_type")
                params.putAll([
                        "response_type": "refresh_token",
                        "UserID": info.extId().getOrElse(""),
                        "client_id": ctx.clientId(),
                        "client_secret": ctx.clientSecret()
                ])
                params.put("redirect_uri", ctx.parameters()["redirectUri"])
                return new Good(req.withQueryParams(params))
            case Phase.undef:
            case Phase.subscribe:
            case Phase.unsubscribe:
            case Phase.fetch:
                params.put("redirect_uri", ctx.parameters()["redirectUri"])
                return new Good(req.withQueryParams(params))
            default:
                return super.signAndPrepare(ctx, req, info, phase)
        }
    }

    @Override
    def Or<NotificationResponse, Failure> onNotification(Context ctx, RequestDef req) {
        def json = slurper.parseText(req.content())
        def notif = json.collect { collection ->
            String extId = collection['UserID']
            String kind = collection['CollectionType']
            long epochInSeconds = (DateTime.parse(collection['MDate'] as String, mdateFormat).getMillis() / 1000l).toLong()
            def endpoint = ctx.parameters()["endpoint"]
            def reqToDo = new RequestDef("${endpoint}/user/${extId}/${kind}.json").withQueryParams([
                    client_id: ctx.clientId(),
                    client_secret: ctx.clientSecret(),
                    sc: ctx.parameters()[kind + "_sc"],
                    sv: ctx.parameters()[kind + "_sv"],
                    locale: "default",
                    start_time: epochInSeconds.toString(),
                    end_time: (epochInSeconds + 1).toString()
            ])
            new ThirdPartyNotification(new ByExternalId(extId), [reqToDo])
        }
        new Good(new NotificationResponse(notif))
    }

    @Override
    def Or<RequestDef, Failure> fetch(Context ctx, RequestDef req, DeviceInfo info) {
        new Good(req.addQueryParams(["access_token" : info.credentials().token()]))
    }

    @Override
    def Or<List<Event>, Failure> onFetchResponse(Context ctx, RequestDef req, DeviceInfo info, Response res) {
        ctx.debug("receive: " + res)
        def json = slurper.parseText(res.content())
        int pageCount = json['PageNumber'] as int
        if (pageCount > 1) {
            ctx.debug("some data will not be retreived, multi-pages response is not supported. nb pages : " + pageCount)
        }

        def events = json
                .findAll{Map.Entry kv -> kv.key.toString().endsWith("DataList")}
                .collectMany{kv0 ->
            Map.Entry kv = kv0 as Map.Entry
            kv.value.collect { data ->
                //MDate is an "epoch" in the TimeZone of Time, not in GMT like correct epoch.
                def tz = data['TimeZone']
                int delta = (tz == -1) ? 0 : Integer.parseInt((tz as String).replace("+", "")) * 36
                String dateKey = data.findResult{Map.Entry kv2 ->  if (kv2.key  == 'MDate' || kv2.key.toString().endsWith("StartTime")) kv2.key else null }
                long seconds = data[dateKey] as long
                long ts = (seconds - delta) * 1000l
                new Event(ts, JsonOutput.toJson(data))
            }}
        ctx.debug("back : " + events)
        new Good(events)
    }
}
