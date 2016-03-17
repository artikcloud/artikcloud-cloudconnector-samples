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
        def content = req.content()
        def json = slurper.parseText(content)
        def extId = json.user.id.toString() 
        def pushSecret = ctx.parameters.pushSecret  

        if (extId == null) {
            ctx.debug('Bad notification (where is did in following req : ) ' + req)
            return new Bad(new Failure('Impossible to recover device id from token request.'))
        }
        if (json.secret == null || json.secret != pushSecret){
            ctx.debug("Invalid secret hash for callback request $req ; expected : $jawboneCallbackSecretHash")
            return new Bad(new Failure("Invalid secret hash"))
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
            })
        def flattenedJson = flatten(checkinFiltered).subMap(["user.id", "createdAt", "timeZoneOffset", "venue.location.lat", "venue.location.lng"])
        def aimJson = renameJson(flattenedJson)
        return new Good([new Event(timestamp, JsonOutput.toJson(aimJson).trim())])
    }

    private def renameJson(json) {
        transformJson(json, { k, v ->
            if (k == "venue.location.lng")
                ["long": (v)]
            else if (k == "createdAt")
                ["timestamp": (v)]
            else if (k == "user.id")
                ["id": (v)]
            else if (k == "venue.location.lat")
                ["lat": (v)]
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

    Map flatten(Map m, String separator = '.') { 
        m.collectEntries { 
            k, v ->  v instanceof Map ? 
                flatten(v, separator).collectEntries { 
                    q, r ->  [(k + separator + q): r] 
                } : [(k):v] 
        } 
    }

}

