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
import scala.Option

//@CompileStatic
class MyCloudConnector extends CloudConnector {
    static mdateFormat = DateTimeFormat.forPattern('yyyy-MM-dd HH:mm:ss').withZoneUTC()
    static final CT_JSON = 'application/json'
    static final allowedKeys = [
        "createdAt", 
        "timeZoneOffset", 
        "venue", "location", "lat", "lng"
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
        oscilo(ctx, json)
        oscilo(ctx, json.response.user.id)
        new Good(Option.apply(info.withExtId(json.response.user.id)))//.withExtId("1")))//.withExtId(json.response.user.id)))
    }

    // CALLBACK
    // -----------------------------------------
    @Override
    Or<NotificationResponse, Failure> onNotification(Context ctx, RequestDef req) {
        def json = slurper.parseText(req.content)
        def pushSecret = ctx.parameters()["pushSecret"]
        ctx.debug("pushSecret " + pushSecret)

        if (json.secret == [] || json.secret.any {it.trim() != pushSecret.trim()}){
            ctx.debug("Invalid secret hash for callback request $req ; expected : $pushSecret")
            return new Bad(new Failure("Invalid push secret"))
        }
        
        def checkin = json.checkin.collect { e -> slurper.parseText(e)}
        def extId = checkin.collect { e -> e.user.id.toString()}
        ctx.debug("extId " + extId)
        
        if (extId.size() != checkin.size() || extId.any {it == null}) {
            ctx.debug('Bad notification (where is did in following req : ) ' + req)
            return new Bad(new Failure('Impossible to recover device id from token request.'))
        }

        def checkinFiltered = checkin.collect { e ->
            transformJson(e, { k, v ->
                if ((allowedKeys + extIdKeys).contains(k))
                    [(k): (v)]  
                else
                    [:]
            })
        }
        checkinFiltered.each {oscilo(ctx, it)}
        ctx.debug("checkinFiltered " + checkinFiltered)

        def notifications = checkinFiltered.collect { e ->
            def eid = e.user.id
            def eFiltered = transformJson(e, { k, v ->
                if (allowedKeys.contains(k))
                    [(k): (v)]  
                else
                    [:]
            })
            def aimJson = renameJson(eFiltered)
            def dataToPush = JsonOutput.toJson(aimJson)
            new ThirdPartyNotification(new ByExternalDeviceId(eid), [], [dataToPush])
        }
        
        return new Good(new NotificationResponse(notifications))
            
    }
    
    // 5. Parse and check (authorisation) pushed data (data come from Notification and can be transformed)
    
    @Override
    def Or<List<Event>, Failure> onNotificationData(Context ctx, DeviceInfo info, String data) {
        def json = slurper.parseText(data)
        json.timestamp = (json.timestamp)? json.timestamp * 1000L: ctx.now()
        return new Good([new Event(json.timestamp, JsonOutput.toJson(json))])
    }

    private def renameJson(json) {
        transformJson(json, { k, v ->
            if (k == "lng")
                ["long": v]
            else if (k == "createdAt")
                ["timestamp": v]
            else
                [(k):v]
        })
    }

    private def transformJson(obj, f) {
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
