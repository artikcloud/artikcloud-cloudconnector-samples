package cloudconnector

import cloud.artik.cloudconnector.api_v1.*
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.scalactic.*
import scala.Option

import static java.net.HttpURLConnection.*
class MyCloudConnector extends CloudConnector {

	static final CT_JSON = 'application/json'
	def JsonSlurper slurper = new JsonSlurper()

	// --------------------------------------
	// Prepare requests
	// --------------------------------------

	def Or<RequestDef, Failure> signAndPrepare(Context ctx, RequestDef req, DeviceInfo info, Phase phase)
	{
		Map params = [:]
		params.putAll(req.queryParams())
		switch (phase)
		{
			case Phase.refreshToken:
				params.put("client_id", ctx.clientId())
				params.put("client_secret", ctx.clientSecret())
				params.put([
					"refresh_token",
					info.credentials().refreshToken().get()
				])
				params.put("grant_type", "refresh_token")
				new Good(req.withBodyParams(params).withQueryParams([:]))
				break
			case Phase.subscribe:
			case Phase.unsubscribe:
			case Phase.undef:
			case Phase.fetch:
				params.put([
					"access_token",
					info.credentials().token()
				])
				new Good(req.withQueryParams(params))
				break
			default:
				super.signAndPrepare(ctx, req, info, phase)
		}
	}

	// --------------------------------------
	// Callbacks
	// --------------------------------------

	@Override
	Or<NotificationResponse, Failure> onNotification(Context ctx, RequestDef req)
	{
		if (!req.content?.trim() || req.content == "AnyContentAsEmpty")
			return new Good(new NotificationResponse([]))

		if (req.url.endsWith("thirdpartynotifications/postsubscription"))
		{
			def json = slurper.parseText(req.content())

			return new Good(new NotificationResponse([
				new ThirdPartyNotification(new ByDid(json.did), [netatmoGetThermostatsData()])
			]))
		}

		return new Good(new NotificationResponse([]))
	}

	// --------------------------------------
	// Process responses
	// --------------------------------------
	@Override
	Or<List<Event>, Failure> onFetchResponse(Context ctx, RequestDef req, DeviceInfo info, Response res) {
		switch (res.status) {
			case HTTP_OK:
				def content = res.content.trim()
				if (content == '' || content == 'OK')
				{
					return new Good([])
				}
				else if (res.contentType.startsWith(CT_JSON))
				{
					if (req.url.endsWith("getthermostatsdata"))
					{
						// synchronizeDevices action
						if(req.passThrough.size() == 0)
							return synchronizeNetatmoThermostats(ctx, info, content)

						// getAllData action
						if(req.passThrough.size() == 1)
							return getAllDataFromNetatmoThermostats(ctx, info, content)

						// getData action
						if(req.passThrough.size() == 3)
						{
							def relayId = req.passThrough["relayId"]
							def moduleId = req.passThrough["moduleId"]
							return getDataFromNetatmoThermostat(ctx, info, content, relayId, moduleId)
						}
					}

					if(req.url.endsWith("setthermpoint"))
						return checkNetatmoResponseStatus(req, content)

					return new Bad(new Failure("unsupported request ${req.url}"))
				}
				return new Bad(new Failure("unsupported response ${res} ... ${res.contentType} .. ${res.contentType.startsWith(CT_JSON)}"))
			default:
				return new Bad(new Failure("http status : ${res.status} is not OK (${HTTP_OK}) on ${req.method} ${req.url}"))
		}
	}
	// --------------------------------------
	// Main CC functions
	// --------------------------------------

	def synchronizeNetatmoThermostats(ctx, info, content)
	{
		def events = []
		def thermostatSubExtIds = [] // to detect deleted thermostats from Netatmo account
		def thermostatsData = slurper.parseText(content)
		def devices = thermostatsData["body"]["devices"]

		for(device in devices)
			for(module in device["modules"])
		{
			events << createNetatmoThermostatSubDevice(ctx, device, module)
			events << updateNetatmoThermostatSubDeviceStatus(ctx, device, module)

			thermostatSubExtIds << netatmoThermostatSubExtIdStrFromDeviceAndModule(device, module)
		}

		events.addAll(cleanNetatmoThermostatSubDevices(ctx, info, thermostatSubExtIds))

		return new Good(events)
	}

	def getAllDataFromNetatmoThermostats(ctx, info, content)
	{
		def events = []
		def thermostatsData = slurper.parseText(content)
		def devices = thermostatsData["body"]["devices"]
		for(device in devices)
			for(module in device["modules"])
				events << updateNetatmoThermostatSubDeviceStatus(ctx, device, module)
		return new Good(events)
	}

	def getDataFromNetatmoThermostat(ctx, info, content, relayId, moduleId)
	{
		def events = []
		def thermostatsData = slurper.parseText(content)
		def devices = thermostatsData["body"]["devices"]
		for(device in devices)
			if(device._id == relayId)
				for(module in device["modules"])
					if(module._id == moduleId)
						events << updateNetatmoThermostatSubDeviceStatus(ctx, device, module)
		return new Good(events)
	}

	def createNetatmoThermostatSubDevice(ctx, device, module)
	{
		def prefix = "Netatmo "

		def thermostatName = module["module_name"]
		def relayId = device["_id"]
		def moduleId = module["_id"]

		def subDeviceExtId = netatmoThermostatSubExtId(relayId, moduleId)
		def subDeviceName = formatAKCDeviceName(prefix + " " + thermostatName)
		def subDeviceExtType = Option.apply("thermostat")

		ctx.debug("- Creating sub-device: " + subDeviceName + " as " + subDeviceExtType.getOrElse("undefined") )
		return new Event(ctx.now(), subDeviceName, EventType.createOrUpdateDevice, subDeviceExtId, subDeviceExtType)
	}

	def updateNetatmoThermostatSubDeviceStatus(ctx, device, module)
	{
		def relayId = device["_id"]
		def moduleId = module["_id"]
		def subDeviceExtId = netatmoThermostatSubExtId(relayId, moduleId)
		def messageData = extractNetatmoThermostatStatus(device, module)
		ctx.debug("- Updating the sub-device status: " + JsonOutput.toJson(messageData) )
		return new Event(ctx.now(), JsonOutput.toJson(messageData), EventType.data, subDeviceExtId)
	}

	def cleanNetatmoThermostatSubDevices(ctx, info, thermostatSubExtIds)
	{
		def events = []
		def subDeviceExtIds = ctx.findExtSubDeviceId(info) // all subdevice external IDs (as List[String])
		for(subDeviceExtId in subDeviceExtIds)
			if(!(subDeviceExtId in thermostatSubExtIds))
				events << new Event(ctx.now(), "", EventType.deleteDevice, Option.apply(subDeviceExtId))
		return events
	}

	def checkNetatmoResponseStatus(req, content)
	{
		def responseStatus = slurper.parseText(content)
		if(responseStatus["status"].equals("ok"))
		{
			// refresh thermostat status
			def relayId = req.queryParams["device_id"]
			def moduleId = req.queryParams["module_id"]
			def getDataReq = getDataReqDef(relayId, moduleId).withDelay(1000)
			return new Good([getDataReq])
		}

		else
			return new Bad(new Failure("Error reported from Netatmo API: " + content))
	}

	// --------------------------------------
	// Actions
	// --------------------------------------
	@Override
	Or<ActionResponse, Failure> onAction(Context ctx, ActionDef action, DeviceInfo info)
	{
		if(action.extSubDeviceId().isEmpty())
		{
			// Parent
			switch (action.name)
			{
				// Synchronize all sub-devices
				case "synchronizeDevices":
					return synchronizeDevices()

				case "getAllData":
					return getAllData()
			}
		}
		else
		{
			// Sub-devices
			def subDeviceExtId = action.extSubDeviceId().get()
			def subDeviceExtDTID = ctx.findExtSubDeviceTypeIdByExtSubDeviceId(info, subDeviceExtId).getOrElse("")

			if(subDeviceExtDTID.equals("thermostat"))
			{
				def thermostatSubExtId = subDeviceExtId
				ctx.debug("thermostatSubExtId = " + thermostatSubExtId)
				def relayId = relayId(thermostatSubExtId) // also known as device_id in Netatmo API
				def moduleId = moduleId(thermostatSubExtId)

				switch (action.name)
				{
					case "getData":
						return getData(relayId, moduleId)

					case "setMode":
						def actionParams = slurper.parseText(action.params)
						return netatmoSetThermostatSetpointMode(relayId, moduleId, actionParams.mode)

					case "setProgramMode":
					// follow a weekly schedule
						return netatmoSetThermostatSetpointMode(relayId, moduleId, "program")

					case "setAwayMode":
					// apply the "away" temperature as defined by the user
						return netatmoSetThermostatSetpointMode(relayId, moduleId, "away")

					case "setHgMode":
					// frost-guard
						return netatmoSetThermostatSetpointMode(relayId, moduleId, "hg")

					case "setManualMode":
					// apply a temperature setpoint, already set manually before
						return netatmoSetThermostatSetpointMode(relayId, moduleId, "manual")

					case "setOffMode":
					// off
						return netatmoSetThermostatSetpointMode(relayId, moduleId, "off")

					case "setMaxMode":
					// heating continuously
						return netatmoSetThermostatSetpointMode(relayId, moduleId, "max")

					case "setTemperature":
					// apply a manually set temperature setpoint
						def actionParams = slurper.parseText(action.params)
						return netatmoSetThermostatSetpointTemperature(relayId, moduleId, actionParams.temp)

					case "setTemperatureDuring":
					// apply a manually set temperature setpoint for a finite duration in minutes (wrapper)
						def actionParams = slurper.parseText(action.params)
						return netatmoSetThermostatSetpointTemperatureDuring(ctx, relayId, moduleId, actionParams.temp, actionParams.duration)
				}
			}

		}
		// Unsupported action
		return new Bad(new Failure("Unknown action: ${action.name}"))

	}

	def synchronizeDevices()
	{
		return new Good(new ActionResponse([
			new ActionRequest([netatmoGetThermostatsData()])
		]))
	}

	def getAllData()
	{
		return new Good(new ActionResponse([
			new ActionRequest([
				netatmoGetThermostatsData().withPassThrough(["akcAction": "getAllData"])
			])
		]))
	}

	def getDataReqDef(relayId, moduleId)
	{
		return netatmoGetThermostatsData().withPassThrough(["akcAction": "getData", "relayId":relayId, "moduleId":moduleId])
	}

	def getData(relayId, moduleId)
	{
		return new Good(new ActionResponse([
			new ActionRequest([
				getDataReqDef(relayId, moduleId)
			])
		]))
	}

	// --------------------------------------
	// Netatmo Thermostat API
	// --------------------------------------

	static final API_URL = "https://api.netatmo.com/api"

	// Discovery
	def netatmoGetThermostatsData()
	{
		return new RequestDef(API_URL + "/getthermostatsdata")
				.withMethod(HttpMethod.Get)
	}

	// Commands
	def netatmoSetThermostatSetpointMode(relayId, moduleId, mode)
	{
		def req = new RequestDef(API_URL + "/setthermpoint")
				.withQueryParams(["device_id":relayId,
					"module_id":moduleId,
					"setpoint_mode":mode
				])
		return new Good(new ActionResponse([new ActionRequest([req])]))
	}

	def netatmoSetThermostatSetpointTemperature(relayId, moduleId, temperature)
	{
		def req = new RequestDef(API_URL + "/setthermpoint")
				.withQueryParams(["device_id":relayId,
					"module_id":moduleId,
					"setpoint_mode":"manual",
					"setpoint_temp":temperature.toString()
				])
		return new Good(new ActionResponse([new ActionRequest([req])]))
	}

	def netatmoSetThermostatSetpointTemperatureDuring(ctx, relayId, moduleId, temperature, duration)
	{
		def req = new RequestDef(API_URL + "/setthermpoint")
				.withQueryParams(["device_id":relayId,
					"module_id":moduleId,
					"setpoint_mode":"manual",
					"setpoint_temp":temperature.toString(),
					"setpoint_endtime": (ctx.now().intdiv(1000) + duration * 60).toString()
				])
		return new Good(new ActionResponse([new ActionRequest([req])]))
	}

	// --------------------------------------
	// Helpers to extract Netatmo data
	// --------------------------------------

	def extractNetatmoThermostatStatus(device, module)
	{
		def message = ["temp": module.measured.temperature,
			"setpointTemp": module.measured.setpoint_temp,
			"batteryLevel": module.battery_percent,
			"radioStatus": convertNetatmoThermostatRadioStatusToSignalQuality(module.rf_status),
			"batteryStatus": convertNetatmoBatteryVPToBatteryLevel(module.battery_vp),
			"wifiStatus": convertNetatmoThermostatWiFiStatusToSignalQuality(device.wifi_status),
			"radioStatusValue":module.rf_status,
			"wifiStatusValue":device.wifi_status,
			"battery_vp":module.battery_vp
		]

		if("setpoint" in module)
			if(module.setpoint.setpoint_mode == "manual")
				message["setpointTemp"] = module.setpoint.setpoint_temp

		return message
	}

	def convertNetatmoThermostatRadioStatusToSignalQuality(rf_status)
	{
		// https://dev.netatmo.com/dev/resources/technical/reference/thermostat/getthermostatsdata
		if(rf_status >= 90)
			return "low"

		if(rf_status >= 80)
			return "medium"

		if(rf_status >= 70)
			return "high"

		if(rf_status >= 60)
			return "full signal"

		return "full signal"
	}

	def convertNetatmoBatteryVPToBatteryLevel(battery_vp)
	{
		// https://dev.netatmo.com/dev/resources/technical/reference/thermostat

		if(battery_vp >= 4100)
			return "full"

		if(battery_vp >= 3600)
			return "high"

		if(battery_vp >= 3300)
			return "medium"

		if(battery_vp >= 300)
			return "low"

		if(battery_vp < 300)
			return "very low"
	}

	def convertNetatmoThermostatWiFiStatusToSignalQuality(wifi_status)
	{
		// https://dev.netatmo.com/dev/resources/technical/reference/thermostat

		if(wifi_status >= 86)
			return "bad"

		if(wifi_status >= 71)
			return "average"

		if(wifi_status >= 56)
			return "good"

		return "good"
	}

	// --------------------------------------
	// CC helpers
	// --------------------------------------

	static final SEPARATOR = '-'

	def netatmoThermostatSubExtIdStrFromDeviceAndModule(device, module)
	{
		def relayId = device["_id"]
		def moduleId = module["_id"]
		return netatmoThermostatSubExtIdStr(relayId, moduleId)
	}

	def netatmoThermostatSubExtIdStr(relayId, moduleId)
	{
		return relayId + SEPARATOR + moduleId
	}

	def netatmoThermostatSubExtId(relayId, moduleId)
	{
		return Option.apply(netatmoThermostatSubExtIdStr(relayId, moduleId))
	}

	def relayId(thermostatSubExtId)
	{
		if(!thermostatSubExtId.contains(SEPARATOR))
			return null

		def tokens = thermostatSubExtId.split(SEPARATOR)

		if(tokens.length != 2)
			return null

		return tokens[0]
	}

	def moduleId(thermostatSubExtId)
	{
		if(!thermostatSubExtId.contains(SEPARATOR))
			return null

		def tokens = thermostatSubExtId.split(SEPARATOR)

		if(tokens.length != 2)
			return null

		return tokens[1]
	}

	// --------------------------------------
	// Helpers
	// --------------------------------------

	def formatAKCDeviceName(deviceName)
	{
		deviceName = deviceName.substring(0, Math.min(deviceName.length(), 64))
		deviceName = deviceName.replaceAll("'", "_")
		return deviceName
	}
}