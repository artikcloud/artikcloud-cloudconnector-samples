package cloudconnector

import cloud.artik.cloudconnector.api_v1.*
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.scalactic.*
import scala.Option

import static java.net.HttpURLConnection.*

class MyCloudConnector extends CloudConnector {
    static final ALLOWED_KEYS = [
            "away_temperature_high_c", "away_temperature_low_c", "ambient_temperature_c","away",
            "can_cool", "can_heat",
            "device_id",
            "fan_timer_active", "fan_timer_timeout",
            "has_fan", "has_leaf", "hvac_mode", "humidity", "hvac_state",
            "is_online", "is_using_emergency_heat",
            "last_connection",
            "name", "name_long",
            "structure_id",
            "target_temperature_c", "target_temperature_high_c", "target_temperature_low_c",
            "structure", "away", "name"
    ]
    static final ENDPOINT = "https://developer-api.nest.com"
    static final DEVICE_ID_KEY = 'device_id'
    static final DEVICE_NAME_KEY = 'name'
    static final STRUCTURE_ID_KEY = 'structure_id'
    static final STRUCTURE_NAME_KEY = 'name'

    def JsonSlurper slurper = new JsonSlurper()

    def Or<RequestDef, Failure> signAndPrepare(Context ctx, RequestDef req, DeviceInfo info, Phase phase) {
        Map params = [:]
        params.putAll(req.queryParams())
        switch (phase) {
            case Phase.undef:
            case Phase.subscribe:
            case Phase.unsubscribe:
            case Phase.fetch:
                return new Good(req.addHeaders(["Authorization": "Bearer " + info.credentials.token]))
                break
            case Phase.refreshToken:
            case Phase.getOauth2Token:
            default:
                super.signAndPrepare(ctx, req, info, phase)
                break
        }
    }

    @Override
    Or<List<RequestDef>, Failure> subscribe(Context ctx, DeviceInfo info) {
        def req = new RequestDef(ENDPOINT)
        new Good([req])
    }

    @Override
    Or<Option<DeviceInfo>,Failure> onSubscribeResponse(Context ctx, RequestDef req,  DeviceInfo info, Response res) {
        if (res.status == HTTP_OK) {
            def json = slurper.parseText(res.content)
            def deviceList = json?.devices?.get("${ctx.parameters().productType}s")?.values() as List
            def structureList = json?.structures?.values() as List
            def userData = generateName2IdsPairs(deviceList, 'devices', DEVICE_NAME_KEY, DEVICE_ID_KEY) +
                            generateName2IdsPairs(structureList, 'structures', STRUCTURE_NAME_KEY, STRUCTURE_ID_KEY)
            return new Good(Option.apply(info.withUserData(JsonOutput.toJson(userData))))
        } else {
            return new Bad(new Failure("onSubscribeResponse response status ${res.status} $res"))
        }
    }

    @Override
    def Or<Task, Failure> onNotification(Context ctx, RequestDef req) {
        if (req.url.endsWith("thirdpartynotifications/postsubscription")) {
            def json = slurper.parseText(req.content())
            return new Good(new NotificationResponse([new ThirdPartyNotification(new ByDid(json.did), 
                [new RequestDef(ENDPOINT).withHeaders(["X-Artik-Action": 'synchronizeDevices'])]
            )]))
        } else {
            return new Bad(new Failure("unsupported parameters"))
        }
    }

    @Override
    def Or<ActionResponse, Failure> onAction(Context ctx, ActionDef action, DeviceInfo info) {
        //To avoid that a empty UserData interrupt Action getAllData
        if (action.name == "getAllData" || action.name == "synchronizeDevices") {
            return new Good(new ActionResponse([new ActionRequest(
                [new RequestDef(ENDPOINT).withHeaders(["X-Artik-Action": action.name])]
            )]))
        }
        def json = slurper.parseText(action?.params ?: "{}")
        if (info.userData.isEmpty()) {
            return new Bad(new Failure("Can not get userData"))
        }
        def userData = info.userData.map{ data -> slurper.parseText(data) }.get()
        def deviceIdList = idListByName(userData, 'devices', json?.deviceName)
        def structureIdList = idListByName(userData, 'structures', json?.structureName)
        //Other action except getAllData :
        switch (action.name) {
            case "setTemperature":
                deviceIdList = action.extSubDeviceId().isEmpty() ? [] : [action.extSubDeviceId().get()] // toList doesn't convert well
            case "setTemperatureByDeviceId":
                deviceIdList = json.deviceId ? [json.deviceId] : deviceIdList
            case "setTemperatureByDeviceName":
                def params = ["target_temperature_c": json.temp]
                def goodRequestsOrBad = combine(deviceIdList.collect { id ->
                    def urls = [
                      ["root": "${ENDPOINT}/devices/${ctx.parameters().productType}s"],
                      ["deviceId": id]
                    ]
                    nestApiRequest(urls, params, action.name)
                })
                return collectOnOr(goodRequestsOrBad, { orList ->
                    new ActionResponse([new ActionRequest(orList)])
                })

            case "setTemperatureInFahrenheit":
                deviceIdList = action.extSubDeviceId().isEmpty() ? [] : [action.extSubDeviceId().get()] // toList doesn't convert well
            case "setTemperatureInFahrenheitByDeviceId":
                deviceIdList = json.deviceId ? [json.deviceId] : deviceIdList
            case "setTemperatureInFahrenheitByDeviceName":
                def params = ["target_temperature_f": json.temp]
                def goodRequestsOrBad = combine(deviceIdList.collect { id ->
                    def urls = [
                      ["root": "${ENDPOINT}/devices/${ctx.parameters().productType}s"],
                      ["deviceId": id]
                    ]
                    nestApiRequest(urls, params, action.name)
                })
                return collectOnOr(goodRequestsOrBad, { orList ->
                    new ActionResponse([new ActionRequest(orList)])
                })

            case "setOff":
                deviceIdList = action.extSubDeviceId().isEmpty() ? [] : [action.extSubDeviceId().get()] // toList doesn't convert well
            case "setOffByDeviceId":
                deviceIdList = json.deviceId ? [json.deviceId] : deviceIdList
            case "setOffByDeviceName":
                def params = ["hvac_mode": "off"]
                def goodRequestsOrBad = combine(deviceIdList.collect { id ->
                    def urls = [
                      ["root": "${ENDPOINT}/devices/${ctx.parameters().productType}s"],
                      ["deviceId": id]
                    ]
                    nestApiRequest(urls, params, action.name)
                })
                return collectOnOr(goodRequestsOrBad, { orList ->
                    new ActionResponse([new ActionRequest(orList)])
                })

            case "setHeatMode":
                deviceIdList = action.extSubDeviceId().isEmpty() ? [] : [action.extSubDeviceId().get()] // toList doesn't convert well
            case "setHeatModeByDeviceId":
                deviceIdList = json.deviceId ? [json.deviceId] : deviceIdList
            case "setHeatModeByDeviceName":
                def params = ["hvac_mode": "heat"]
                def goodRequestsOrBad = combine(deviceIdList.collect { id ->
                    def urls = [
                      ["root": "${ENDPOINT}/devices/${ctx.parameters().productType}s"],
                      ["deviceId": id]
                    ]
                    nestApiRequest(urls, params, action.name)
                })
                return collectOnOr(goodRequestsOrBad, { orList ->
                    new ActionResponse([new ActionRequest(orList)])
                })

            case "setCoolMode":
                deviceIdList = action.extSubDeviceId().isEmpty() ? [] : [action.extSubDeviceId().get()] // toList doesn't convert well
            case "setCoolModeByDeviceId":
                deviceIdList = json.deviceId ? [json.deviceId] : deviceIdList
            case "setCoolModeByDeviceName":
                def params = ["hvac_mode": "cool"]
                def goodRequestsOrBad = combine(deviceIdList.collect { id ->
                    def urls = [
                      ["root": "${ENDPOINT}/devices/${ctx.parameters().productType}s"],
                      ["deviceId": id]
                    ]
                    nestApiRequest(urls, params, action.name)
                })
                return collectOnOr(goodRequestsOrBad, { orList ->
                    new ActionResponse([new ActionRequest(orList)])
                })

            case "setHeatCoolMode":
                deviceIdList = action.extSubDeviceId().isEmpty() ? [] : [action.extSubDeviceId().get()] // toList doesn't convert well
            case "setHeatCoolModeByDeviceId":
                deviceIdList = json.deviceId ? [json.deviceId] : deviceIdList
            case "setHeatCoolModeByDeviceName":
                def params = ["hvac_mode": "heat-cool"]
                def goodRequestsOrBad = combine(deviceIdList.collect { id ->
                    def urlNodes = [
                      ["root": "${ENDPOINT}/devices/${ctx.parameters().productType}s"],
                      ["deviceId": id]
                    ]
                    nestApiRequest(urlNodes, params, action.name)
                })
                return collectOnOr(goodRequestsOrBad, { orList ->
                    new ActionResponse([new ActionRequest(orList)])
                })

            case "setHomeByStructureId":
                structureIdList = [json.structureId]
            case "setHomeByStructureName":
                def params = ["away": "home"]
                def goodRequestsOrBad = combine(structureIdList.collect { id ->
                    def urls = [
                      ["root": "${ENDPOINT}/structures"],
                      ["structureId": id]
                    ]
                    nestApiRequest(urls, params, action.name)
                })
                return collectOnOr(goodRequestsOrBad, { orList ->
                    new ActionResponse([new ActionRequest(orList)])
                })

            case "setAwayByStructureId":
                structureIdList = [json.structureId]
            case "setAwayByStructureName":
                def params = ["away": "away"]
                def goodRequestsOrBad = combine(structureIdList.collect { id ->
                    def urls = [
                      ["root": "${ENDPOINT}/structures"],
                      ["structureId": id]
                    ]
                    nestApiRequest(urls, params, action.name)
                })
                return collectOnOr(goodRequestsOrBad, { orList ->
                    new ActionResponse([new ActionRequest(orList)])
                })

            default:
                return new Bad(new Failure("unsupported action for nest:" + action.name))
        }
    }

    @Override
    def Or<List<Event>, Failure> onFetchResponse(Context ctx, RequestDef req, DeviceInfo info, Response res) {
        if (res.status != HTTP_OK) {
            return new Bad(new Failure("[${info.did}] onFetchResponse got status http status : ${res.status()}) with content: ${res.content()}"))
        }
        def json = slurper.parseText(res?.content?:"{}")
        def deviceList = json?.devices?.get("${ctx.parameters().productType}s")?.values() as List
        def structureList = json?.structures?.values() as List
        switch(req.headers()['X-Artik-Action']) {
            case 'getAllData':
                //json/devices/thermostats/Map<THERMO_ID, THERMO_VALUE>
                def dataEvents = deviceList?.collect { device ->
                    new Event(ctx.now(), outputJson(device), EventType.data, Option.apply(device.device_id))
                } ?: []
                return new Good(dataEvents)
            case 'synchronizeDevices':
                def prefix = ctx.parameters()["prefix"] ?: ""
                def suffix = ctx.parameters()["suffix"] ?: ""
                def userData = generateName2IdsPairs(deviceList, 'devices', DEVICE_NAME_KEY, DEVICE_ID_KEY) +
                                generateName2IdsPairs(structureList, 'structures', STRUCTURE_NAME_KEY, STRUCTURE_ID_KEY)
                def userDataEvents = [ new Event(ctx.now(), JsonOutput.toJson(userData), EventType.user) ]

                def dName2Ids = userData?.get('devices') // Map<String, List<String>>
                def newExtDeviceIds = dName2Ids?.collectMany { name, list -> list }
                def existingExtDeviceIds = ctx.findExtSubDeviceId(info)
                def addingExtDeviceEvents = newExtDeviceIds.collect { thisId ->
                    def dName = dName2Ids.find {name, list -> list.contains(thisId)}.key
                    new Event (ctx.now(), "${prefix}${dName}${suffix}".toString(), EventType.createOrUpdateDevice, Option.apply(thisId))
                }
                def deletingExtDeviceEvents = existingExtDeviceIds.minus(newExtDeviceIds).collect { thisId ->
                    new Event (ctx.now(), '', EventType.deleteDevice, Option.apply(thisId))
                }
                return new Good(userDataEvents + addingExtDeviceEvents + deletingExtDeviceEvents)
            case "setTemperature":
            case "setTemperatureByDeviceId":
            case "setTemperatureByDeviceName":
            case "setTemperatureInFahrenheit":
            case "setTemperatureInFahrenheitByDeviceId":
            case "setTemperatureInFahrenheitByDeviceName":
            case "setOff":
            case "setOffByDeviceId":
            case "setOffByDeviceName":
            case "setHeatMode":
            case "setHeatModeByDeviceId":
            case "setHeatModeByDeviceName":
            case "setCoolMode":
            case "setCoolModeByDeviceId":
            case "setCoolModeByDeviceName":
            case "setHeatCoolMode":
            case "setHeatCoolModeByDeviceId":
            case "setHeatCoolModeByDeviceName":
            case "setHomeByStructureId":
            case "setHomeByStructureName":
            case "setAwayByStructureId":
            case "setAwayByStructureName":
                def updatedData = slurper.parseText(req.content())
                def endUrl = req.url.drop(ENDPOINT.size())
                def id = endUrl.split("/").last()
                if (endUrl.contains("structures")){
                    updatedData.put("structure_id", id)
                    updatedData = [structure: updatedData]
                } else {
                    updatedData.put("device_id", id)
                }
                return new Good([new Event(ctx.now(), outputJson(updatedData))])
            default:
                return new Good(Empty.list())
       }
    }



    // Copy-pasted from sami-cloudconnector-samples/open-weather-map/src/main/groovy/io/samsungsami/openWeatherMap/MyCloudConnector.groovy -> transformJson
    // applyToMessage(msg, f) will remove all empty values
    private def applyToMessage(msg, f) {
        if (msg instanceof java.util.Map) {
            msg.collectEntries { k, v ->
                if (v != null) {
                    def newV = applyToMessage(v, f)
                    newV != [:]? f(k, newV): [:]
                } else {
                    [:]
                }
            }
        } else if (msg instanceof java.util.Collection) {
            java.util.Collection newList = msg.collect { item ->
                applyToMessage(item, f)
            }
            newList.isEmpty()? [:]: newList
        } else {
            msg
        }
    }

    //In the entering List<Map>, this Map should include only 1 key-value!, Using List<Map> to ensure collect in order
    def Or<RequestDef, Failure> nestApiRequest(List<Map<String, Object>> urlKeyAndNodes, Map contentParams, String actionHeader) {
        for ( entry in contentParams) {
            if (entry.getValue() == null) {
                return new Bad(new Failure("Null value in Action parameter: " + entry))
            }
        }
        for ( entry in urlKeyAndNodes) {
            if (entry.values().contains(null)) {
                return new Bad(new Failure("Null value in query which item is:" + entry))
            }
        }
        def urlNodes = urlKeyAndNodes.collect { keyAndParam ->
            keyAndParam.values()[0]
        }      
        def request = new RequestDef(urlNodes.join("/"))
                        .withMethod(HttpMethod.Put)
                        .withContent(JsonOutput.toJson(contentParams), "application/json")
                        .withHeaders(["X-Artik-Action": actionHeader])
        return new Good(request)
    }

    //The following function accepts a objList, every obj contains an id & a name, which identified by a varName
    //And this function will extract this kind of relation (name-id pairs)
    def generateName2IdsPairs(List objList, String mapName, String nameKey, String idKey) {
        def nameToId = [:]
        def lowerCaseNameToId = [:]
        // name -> List<id>
        def nullCheckAdd = { map, name, id ->
            if (map[name] == null) {
                map[name] = []
            }
            if (id != null) {
                map[name].add(id)
            }
        }
        objList.each { it ->
            def thisId = it?.get(idKey)
            def thisName = it?.get(nameKey)
            def thisNameLoweCase = thisName?.toLowerCase().trim()
            if (thisName != null) {
                nullCheckAdd(nameToId, thisName, thisId)
                nullCheckAdd(lowerCaseNameToId, thisNameLoweCase, thisId)
            }
        }
        return [(mapName): nameToId] + [(mapName + 'LowerCase'): lowerCaseNameToId]
    }

    def retrieveIdListFromPairs(Map mapStorage, String mapName, String searchedName, Boolean isIgnoreCase = false) {
        if (searchedName == null) {
            return []
        }
        if (searchedName.trim() == '*') {
            return mapStorage?.get(mapName)?.values()?.flatten()?:[]
        }
        if (isIgnoreCase) {
            mapName += 'LowerCase'
            searchedName = searchedName.toLowerCase().trim()
        }
        return mapStorage?.get(mapName)?.get(searchedName)?:[]
    }

    def idListByName(Map mapStorage, String mapName, String name) {
        def idList = retrieveIdListFromPairs(mapStorage, mapName, name)
        if (idList.isEmpty()) {
            idList = retrieveIdListFromPairs(mapStorage, mapName, name, true)
            if (idList.isEmpty()) {
                idList = retrieveIdListFromPairs(mapStorage, mapName, '*')
            }
        }
        return idList
    }
    
    private def outputJson(json) {
        JsonOutput.toJson(
            applyToMessage(json, { k, v ->
                ALLOWED_KEYS.contains(k)? [(k): v]: [:]
            })
        ).trim()
    }


    static <A,B> Or<A,Failure> collectOnOr(Or<B,Failure> or, closure) {
        or.isGood() ? new Good(closure(or.get())) : or
    }
    static <A> Or<List<A>,Failure> combine(List<Or<A,Failure>> list){
        def ret = []
        for ( item in list) {
            if (item.isGood()) {
                ret = ret + [item.get()]
            }else {
                return item
            }
        }
        return new Good(ret)
    }
}
