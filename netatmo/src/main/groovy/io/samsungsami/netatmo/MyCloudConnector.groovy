// Sample CloudConnector, that can be used as a boostrap to write a new CloudConnector.
// Every code is commented, because everything is optional.
// The class can be named as you want no additional import allowed
// See the javadoc/scaladoc of com.samsung.sami.cloudconnector.api.CloudConnector
package io.samsungsami.netatmo

import com.samsung.sami.cloudconnector.api_v1.*
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import org.joda.time.format.*
import org.joda.time.*
import org.scalactic.*

import static java.net.HttpURLConnection.*

class MyCloudConnector extends CloudConnector {
    static final String endpoint = "https://api.netatmo.com/api/"
    static final String stationEndpoint = "${endpoint}getstationsdata"
    def JsonSlurper slurper = new JsonSlurper()



    def Or<RequestDef, Failure> signAndPrepare(Context ctx, RequestDef req, DeviceInfo info, Phase phase){
        println("signeAndPrepare o year")
        Map params = [:]
        params.putAll(req.queryParams())
        switch (phase) {
            case Phase.subscribe:
            case Phase.unsubscribe:
            case Phase.undef:
            case Phase.fetch:
            case Phase.refreshToken:
                new Good(req.addQueryParams(["access_token": info.credentials().token()]))
                break
            default:
                super.signAndPrepare(ctx, req, info, phase)
        }
    }

    @Override
    def  Or<ActionResponse, Failure> onAction(Context ctx, ActionDef action, DeviceInfo dInfo) {
        def req = new RequestDef(stationEndpoint)
        def actionRequests = null
        def json = slurper.parseText(action.params)
        switch (action.name) {
            case "getAllData":
                actionRequests = [new ActionRequest(new BySamiDeviceId(dInfo.did),[req])]
                break
            case "getData":
                if (json.stationId != null){
                    actionRequests = [new ActionRequest(new BySamiDeviceId(dInfo.did),[req])].withQueryParams(["device_Id": json.stationId])
                } else {
                    return new Bad(new Failure("unsupported action for netatmo getData without stationId"))
                }
                break
            default:
                return new Bad(new Failure("unsupported action for netatmo:" + action.name))
                break
        }

        new Good(new ActionResponse(actionRequests))
    }


    @Override
    def Or<List<Event>, Failure> onFetchResponse(Context ctx, RequestDef req, DeviceInfo info, Response res) {
        println("onFetchResponse")
        switch(res.status) {
            case HTTP_OK:
                switch(req.url) {
                    case stationEndpoint:
                        def json = slurper.parseText(res.content().trim())
                        if (json.status != "ok"){
                            return new Bad(new Failure("receiving response with invalid status ${json.status} … ${res}"))
                        }
                        def ts = json.time_server
                        def events = json.body.devices.collect{ data ->
                            def timeZ = data?.place?.timezone ?: null
                            def city = data?.place?.city ?: null
                            def alt = data?.place?.altitude ?: null
                            def lat = data?.place?.location[0] ?: null
                            def longi = data?.place?.location[1] ?: null
                            def temp = data?.dashboard_data?.Temperature ?: null
                            def noise = data?.dashboard_data?.Noise ?: null
                            def humid = data?.dashboard_data?.Humidity ?: null
                            def press = data?.dashboard_data?.Pressure ?: null
                            def absPress = data?.dashboard_data?.AbsolutePressure ?: null
                            def co2 = data?.dashboard_data?.CO2 ?: null
                            def wifiStat = data?.wifi_status ?: null
                            def maxTemp = data?.dashboard_data?.max_temp ?: null
                            def minTemp = data?.dashboard_data?.min_temp ?: null
                            def dateMaxTemp = data?.dashboard_data?.date_max_temp ?: null
                            def dateMinTemp = data?.dashboard_data?.date_min_temp ?: null
                            new Event(ts, '''{"city":''' + city + ''',"altitude":''' + alt + ''',"latitude":''' + lat+ ''',"longitude":''' + longi + '''"timeZone":''' + timeZ
                                    + ''',"wifiStatus":''' + wifiStat
                                    + ''',"temperature":''' + temp + ''',"humidity":''' + humid + ''',"co2":''' + co2 + ''',"noise":''' + noise
                                    + ''',"pressure":''' + press + ''',"absolutePressure":''' + absPress
                                    + ''',"maxTemperature":''' + maxTemp + ''',"minTemperature":''' + minTemp + ''',"dateMaxTemperature":''' + dateMaxTemp + ''',"dateMinTemperature":''' + dateMinTemp + '''}''')
                        }
                        return new Good(events)
                        break
                    default:
                        return new Bad(new Failure("receiving Responsse ${res} fron unknown request ${req.req}"))
                        break
                }
                break
            default:
                return new Bad(new Failure("[${info.did}] onFetchResponse got status http status : ${res.status()}) with content: ${res.content()}"))
        }
    }
}