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
    static final allowedKeys = [
        "createdAt", 
        "timeZoneOffset", 
        "user", "id", 
        "venue", "location", "lat", "lng"
    ]

    JsonSlurper slurper = new JsonSlurper()

    // CALLBACK
    // -----------------------------------------
    @Override
    Or<NotificationResponse, Failure> onNotification(Context ctx, RequestDef req) {
        ctx.debug("Hello world")
        def content = req.bodyParams
        def json = slurper.parseText(content.checkin)
        def extId = json.user.id.toString() 
        def pushSecret = ctx.parameters.pushSecret  

        
        if (extId == null || (!req.contentType.startsWith("application/x-www-form-urlencoded"))) {
            ctx.debug('Bad notification (where is did in following req : ) ' + req)
            return new Bad(new Failure('Impossible to recover device id from token request.'))
        }
        if (content.secret == null || content.secret != pushSecret){
            ctx.debug("Invalid secret hash for callback request $req ; expected : $pushSecret")
            return new Bad(new Failure("Invalid push secret"))
        }

        def dataToPush = json.checkin.collect {e -> JsonOutput.toJson(e)}
        ctx.debug("Data to push : " + dataToPush)

        return new Good(new NotificationResponse([new ThirdPartyNotification(new ByExternalDeviceId(extId), [], dataTopush)]))
    }
    
    // 5. Parse and check (authorisation) pushed data (data come from Notification and can be transformed)
    @Override
    def Or<List<Event>, Failure> onNotificationData(Context ctx, DeviceInfo info, String data) {
        def json = slurper.parseText(data)
        def checkinFiltered = transformJson(json, { k, v ->
                if (allowedKeys.contains(k))
                    [(k): (v)]  
                else
                    [:]
            }).subMap(["user", "createdAt", "timeZoneOffset", "venue"])
        def aimJson = renameJson(checkinFiltered)
        def ts = (aimJson.timestamp)? aimJson.timestamp: ctx.now()
        return new Good([new Event(ts, JsonOutput.toJson(aimJson).trim())])
    }

    private def renameJson(json) {
        transformJson(json, { k, v ->
            if (k == "lng")
                ["long": (v)]
            else if (k == "createdAt")
                ["timestamp": (v * 1000L)]
            else
                [:]
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
