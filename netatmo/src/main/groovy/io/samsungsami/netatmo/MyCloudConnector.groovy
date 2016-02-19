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
        switch (action.name) {
            case "getAllData":
                actionRequests = [new ActionRequest(new BySamiDeviceId(dInfo.did),[req])]
                break
            case "getData":
                def json = slurper.parseText(action.params)
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
        switch(res.status) {
            case HTTP_OK:
                switch(req.url) {
                    case stationEndpoint:
                        def json = slurper.parseText(res.content().trim())
                        if (json.status != "ok"){
                            return new Bad(new Failure("receiving response with invalid status ${json.status} … ${res}"))
                        }
                        def ts = json.time_server
                        def events = json.body.devices.collectMany{ data ->
                            def netatmoId = data._id
                            def modules = data?.modules ?: []
                            def moduleEvents = modules.collectMany{ moduleData ->
                                def windStr = moduleData?.dashboard_data?.WindStrength ?: null
                                def windDir = moduleData?.dashboard_data?.WindAngle ?: null
                                def gustStr = moduleData?.dashboard_data?.GustStrength ?: null
                                def gustDir = moduleData?.dashboard_data?.GustAngle ?: null
                                def dataType = (moduleData?.data_type ?: []).join(", ")
                                def rain = moduleData?.dashboard_data?.Rain ?: null
                                def rainSum1 = moduleData?.dashboard_data?.sum_rain_1 ?: null
                                def rainSum24 = moduleData?.dashboard_data?.sum_rain_24 ?: null
                                def battery = moduleData?.battery_vp ?: null
                                def mType = moduleData?.type ?: null
                                if ((mType != null) && (battery != null) && (dataType != null)) {
                                    [ new Event(ts,
                                            '''{"netatmoId": "''' + netatmoId + '''"'''
                                                    + ''', "module": {"moduleType":"''' + mType + '''", "dataType":''' + optional(dataType) + ''', "battery":''' + battery
                                                    + ''', "gustStrength":''' + gustStr + ''', "gustAngle":''' + gustDir
                                                    + ''', "windStrength":''' + windStr + ''', "windAngle":''' + windDir
                                                    + ''', "rain":''' + rain + ''', "rain1h":''' + rainSum1+ ''', "rain24h":''' + rainSum24
                                                    + '''}}''')
                                    ]
                                } else {
                                    []
                                }
                            }
                            def timeZ = data?.place?.timezone ?: null
                            def city = data?.place?.city ?: null
                            def alt = data?.place?.altitude ?: null
                            def lat = data?.place?.location[0] ?: null
                            def longi = data?.place?.location[1] ?: null
                            def temp = data?.dashboard_data?.Temperature ?: null
                            def noise = data?.dashboard_data?.Noise ?: null
                            def humid = data?.dashboard_data?.Humidity ?: null
                            def press = data?.dashboard_data?.Pressure ?: null
                            def pressTrend = data?.dashboard_data?.pressure_trend ?: null
                            def absPress = data?.dashboard_data?.AbsolutePressure ?: null
                            def co2 = data?.dashboard_data?.CO2 ?: null
                            def wifiStat = data?.wifi_status ?: null
                            def tempTrend = data?.dashboard_data?.temp_trend ?: null
                            def maxTemp = data?.dashboard_data?.max_temp ?: null
                            def minTemp = data?.dashboard_data?.min_temp ?: null
                            def dateMaxTemp = data?.dashboard_data?.date_max_temp ?: null
                            def dateMinTemp = data?.dashboard_data?.date_min_temp ?: null
                            def sType = data?.type ?: null
                            moduleEvents + [new Event(ts, '''{"netatmoId": "''' + netatmoId + '''"'''
                                    + ''', "station": {"city":''' + optional(city) + ''', "altitude":''' + alt + ''', "lat":''' + lat+ ''', "long":''' + longi + ''', "timeZone":''' + optional(timeZ)
                                    + ''', "wifiStatus":''' + wifiStat+ ''', "stationType":''' + optional(sType)
                                    + ''', "temp":''' + temp + ''', "humidity":''' + humid + ''', "co2":''' + co2 + ''', "noise":''' + noise
                                    + ''', "pressure":''' + press + ''', "absolutePressure":''' + absPress
                                    + ''', "pressureTrend":''' + optional(pressTrend) + ''', "tempTrend":''' + optional(tempTrend)
                                    + ''', "maxTemp":''' + maxTemp + ''', "minTemp":''' + minTemp + ''', "dateMaxTemp":''' + dateMaxTemp + ''', "dateMinTemp":''' + dateMinTemp
                                    + '''}}''')]
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

    private def String optional(String str){
        if (str == null)
            null
        else
            "\"" + str + "\""
    }
}