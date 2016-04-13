package io.samsungsami.foursquare

import org.scalactic.*
import org.joda.time.format.DateTimeFormat
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import cloud.artik.cloudconnector.api_v1.*
import scala.Option

//@CompileStatic
class MyCloudConnector extends CloudConnector {
    static mdateFormat = DateTimeFormat.forPattern('yyyy-MM-dd HH:mm:ss').withZoneUTC()
    static final CT_JSON = 'application/json'
    static final allowedKeys = [
        "timeZoneOffset", 
        "venue", "location", "lat", "long",
        "name", "address", "city", "state", "country", "postalCode", "formattedAddress"
    ]
    static final String endpoint = "https://api.foursquare.com/v2/"

    JsonSlurper slurper = new JsonSlurper()

    @Override
    def Or<List<RequestDef>, Failure> subscribe(Context ctx, DeviceInfo info) {
        new Good([new RequestDef(endpoint + "users/self").withQueryParams(["oauth_token" : info.credentials.token, "v" : "20160321"])])
    }

    @Override
    def Or<Option<DeviceInfo>,Failure> onSubscribeResponse(Context ctx, RequestDef req,  DeviceInfo info, Response res) {
        def json = slurper.parseText(res.content)
        new Good(Option.apply(info.withExtId(json.response.user.id)))
    }

    // CALLBACK
    // -----------------------------------------
    @Override
    Or<NotificationResponse, Failure> onNotification(Context ctx, RequestDef req) {
        def json = slurper.parseText(req.content)
        def pushSecret = ctx.parameters()["pushSecret"]

        if (pushSecret == null || json.secret == [] || json.secret.any {it.trim() != pushSecret.trim()}){
            return new Bad(new Failure("Invalid push secret"))
        }
        
        def checkin = json.checkin.collect { e -> slurper.parseText(e)}
        def extId = checkin.collect { e -> e.user.id.toString()}

        if (extId.size() != checkin.size() || extId.any {it == null || it == ""}) {
            return new Bad(new Failure('Impossible to recover device id from request.'))
        }
        def notifications = generateNotificationsFromCheckins(checkin)
        
        return new Good(new NotificationResponse(notifications))
        
    }
    
    // 5. Parse and check (authorisation) pushed data (data come from Notification and can be transformed)
    @Override
    def Or<List<Event>, Failure> onNotificationData(Context ctx, DeviceInfo info, String data) {
        def json = slurper.parseText(data)
        def ts = (json.timestamp)? json.timestamp * 1000L: ctx.now()
        def renamedJson = renameJsonKey(json)
        def jsonFiltered = filterByAllowedKeys(renamedJson, allowedKeys)
        return new Good([new Event(ts, JsonOutput.toJson(jsonFiltered))])
    }

    def filterByAllowedKeys(obj, keepingKeys) {
        transformJson(obj, { k, v ->
            keepingKeys.contains(k)? [(k): v]: [:]
        })
    }

    def generateNotificationsFromCheckins(checkins) {
        checkins.collect { e ->
            def eid = e.user.id
            def dataToPush = JsonOutput.toJson(e)
            new ThirdPartyNotification(new ByExternalId(eid), [], [dataToPush])
        }
    }

    // For key : "lng" --> "long", "createdAt" --> "timestamp"; keep other key-value
    def renameJsonKey(obj) {
        transformJson(obj, { k, v ->
            switch(k) {
                case "lng": return ["long": v]
                case "createdAt": return ["timestamp": v]
                default: return [(k):v]
            }
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
