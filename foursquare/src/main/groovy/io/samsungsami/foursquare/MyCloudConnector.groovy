package io.samsungsami.foursquare
import static java.net.HttpURLConnection.*

import org.scalactic.*
import org.joda.time.format.DateTimeFormat
import org.joda.time.*
import groovy.transform.CompileStatic
import groovy.transform.ToString
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import cloud.artik.cloudconnector.api_v1.*
import org.joda.time.format.ISODateTimeFormat
import scala.Option

//@CompileStatic
class MyCloudConnector extends CloudConnector {
    static mdateFormat = DateTimeFormat.forPattern('yyyy-MM-dd HH:mm:ss').withZoneUTC()
    static final CT_JSON = 'application/json'
    static final allowedKeys = [
        "createdAt", 
        "timeZoneOffset", 
        "venue", "location", "lat", "lng",
        "name", "address", "city", "state", "country", "postalCode", "formattedAddress"
    ]
    static final extIdKeys = [ "user", "id" ]
    static final String endpoint = "https://api.foursquare.com/v2/"

    JsonSlurper slurper = new JsonSlurper()

    @Override
    def Or<List<RequestDef>, Failure> subscribe(Context ctx, DeviceInfo info) {
        new Good([new RequestDef(endpoint + "users/self").withQueryParams(["oauth_token" : info.credentials.token, "v" : "20160321"])])
    }

    @Override
    def Or<Option<DeviceInfo>,Failure> onSubscribeResponse(Context ctx, RequestDef req,  DeviceInfo info, Response res) {
        def json = slurper.parseText(res.content)
        ctx.debug("json.response.user.id " + json.response.user.id)
        new Good(Option.apply(info.withExtId(json.response.user.id)))
    }

    // CALLBACK
    // -----------------------------------------
    @Override
    Or<NotificationResponse, Failure> onNotification(Context ctx, RequestDef req) {
        ctx.debug("req " + req)
        ctx.debug("req.content " + req.content)
        def json = slurper.parseText(req.content)
        def pushSecret = ctx.parameters()["pushSecret"]
        ctx.debug("pushSecret " + pushSecret)

        if (pushSecret == null || json.secret == [] || json.secret.any {it.trim() != pushSecret.trim()}){
            ctx.debug("Invalid secret hash for callback request $req ; expected : $pushSecret")
            return new Bad(new Failure("Invalid push secret"))
        }
        
        def checkin = json.checkin.collect { e -> slurper.parseText(e)}
        def extId = checkin.collect { e -> e.user.id.toString()}
        ctx.debug("extId " + extId)

        if (extId.size() != checkin.size() || extId.any {it == null || it == ""}) {
            ctx.debug('Bad notification (where is did in following req : ) ' + req)
            return new Bad(new Failure('Impossible to recover device id from request.'))
        }

        def checkinFiltered = checkin.collect { e -> filterObjByKeepingKeys(e, allowedKeys + extIdKeys) }
        ctx.debug("checkinFiltered " + checkinFiltered)
        def notifications = generateNotificationsFromCheckins(checkinFiltered)
        
        return new Good(new NotificationResponse(notifications))
        
    }
    
    // 5. Parse and check (authorisation) pushed data (data come from Notification and can be transformed)
    @Override
    def Or<List<Event>, Failure> onNotificationData(Context ctx, DeviceInfo info, String data) {
        def json = slurper.parseText(data)
        json.timestamp = (json.timestamp)? json.timestamp * 1000L: ctx.now()
        return new Good([new Event(json.timestamp, JsonOutput.toJson(json))])
    }

    def filterObjByKeepingKeys(obj, keepingKeys) {
        transformJson(obj, { k, v ->
            if (keepingKeys.contains(k))
                [(k): (v)]  
            else
                [:]
        })
    }

    def generateNotificationsFromCheckins(checkins) {
        checkins.collect { e ->
            def eid = e.user.id
            def eFiltered = filterObjByKeepingKeys(e, allowedKeys)
            def aimJson = renameJson(eFiltered)
            def dataToPush = JsonOutput.toJson(aimJson)
            new ThirdPartyNotification(new ByExternalId(eid), [], [dataToPush])
        }
    }

    // For key : "lng" --> "long", "createdAt" --> "timestamp"; keep other key-value
    def renameJson(obj) {
        transformJson(obj, { k, v ->
            if (k == "lng")
                ["long": v]
            else if (k == "createdAt")
                ["timestamp": v]
            else
                [(k):v]
        })
    }

    // transformJson(obj, f) remove all empty values
    def transformJson(obj, f) {
        if (obj instanceof java.util.Map) {
            obj.collectEntries { k, v ->
                if (v != null) {
                    def newV = transformJson(v, f)
                    if (newV != [:]) {
                        f(k, newV)
                    } else {
                        [:]
                    }
                } else {
                    [:]
                }
            }
        } else if (obj instanceof java.util.Collection) {
            java.util.Collection newList = obj.collect { item ->
                transformJson(item, f)
            }
            (newList.isEmpty()) ? [:] : newList
        } else {
            obj
        }
    }

}
