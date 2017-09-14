package cloudconnector

import cloud.artik.cloudconnector.api_v1.*
import scala.Option
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.scalactic.*

import java.nio.charset.StandardCharsets;
import static java.net.HttpURLConnection.*

class MyCloudConnector extends CloudConnector {
    static final String endpoint = "https://api.lifx.com/v1/lights/"

    static final List<String> allowedEffect = [ "breathe", "pulse" ]

    def JsonSlurper slurper = new JsonSlurper()

    @Override
    def Or<RequestDef, Failure> signAndPrepare(Context ctx, RequestDef req, DeviceInfo info, Phase phase) {
      switch (phase) {
        case Phase.fetch:
          return new Good(req.addHeaders(["Authorization": "Bearer " + info.credentials.token]))
        default:
          super.signAndPrepare(ctx, req, info, phase)
      }
    }

    @Override
    Or<WelcomeTask, Failure> generateWelcomeMessage(Context ctx, DeviceInfo info, Option<RequestDef> req, Option<Response> resp) {
      if (resp.isDefined()) {
        def prefix = ctx.parameters()["prefix"] ?: ""
        def suffix = ctx.parameters()["suffix"] ?: ""
        def content = resp.get().content.trim()
        def lights = slurper.parseText(content ?: "[]")
        if (lights.size() == 0) {
            def msg = "No LiFX Devices have been found on your account.\n"
            return new Good(new WelcomeMessage("No LiFX Devices on your account", msg))
        } else {
          def msg = "The following devices have been added to your account:\n"
          for (light in lights) {
            def name = light.get("label")
            msg += "  - $prefix$name$suffix\n"
          }
          msg += "\n"
          return new Good(new WelcomeMessage("New LiFX Devices on your account", msg))
        }
      } else {
        return new Good(getStateReq("all"))
      }
    }

    @Override
    def Or<NotificationResponse, Failure> onNotification(Context ctx, RequestDef req) {
      if (req.url.endsWith("thirdpartynotifications/postsubscription")) {
        def json = slurper.parseText(req.content())
        return new Good(new NotificationResponse([new ThirdPartyNotification(new ByDid(json.did),[syncReq()])]))
      } else {
        return new Bad(new Failure("unsupported parameters"))
      }
    }

    @Override
    Or<ActionResponse, Failure> onAction(Context ctx, ActionDef action, DeviceInfo info) {
      def actionParams = slurper.parseText(action?.params ?: "{}") ?: [:]
      def selector = "all"
      if (action.extSubDeviceId.isDefined()) {
        selector = "id:" + action.extSubDeviceId.get()
      }
      if (actionParams.containsKey("selector")) {
        selector = actionParams.get("selector")
      }
      switch (action.name) {
        case "synchronizeDevices":
          return new Good(new ActionResponse([new ActionRequest([syncReq()])]))
        case "getLightData":
          return new Good(new ActionResponse([new ActionRequest([getStateReq(selector)])]))
        case "setOn":
          return new Good(new ActionResponse([new ActionRequest([setStateReq(selector, ["power": "on"])])]))
        case "setOff":
          return new Good(new ActionResponse([new ActionRequest([setStateReq(selector, ["power": "off"])])]))
        case "setBrightness":
          return new Good(new ActionResponse([new ActionRequest([setStateReq(selector, actionParams)])]))
        case "setColor":
          return new Good(new ActionResponse([new ActionRequest([setStateReq(selector, actionParams)])]))
        case "setColorRGB":
          def red = actionParams.get("colorRGB").get("red")
          def green = actionParams.get("colorRGB").get("green")
          def blue = actionParams.get("colorRGB").get("blue")
          def color = "rgb:${red},${green},${blue}"
          return new Good(new ActionResponse([new ActionRequest([setStateReq(selector, ["color": color])])]))
        case "toggle":
          return new Good(new ActionResponse([new ActionRequest([toggleReq(selector)])]))
        case "setEffect":
          if (!allowedEffect.contains(actionParams.get("effect"))) {
              return new Bad(new Failure("unsupported effect: " + actionParams.get("effect")))
          }
          return new Good(new ActionResponse([new ActionRequest([setEffectReq("id:" + action.extSubDeviceId().getOrElse(""), actionParams.get("effect"), actionParams)])]))
        default:
          return new Bad(new Failure("unsupported action for LIFX:" + action.name))
        }
    }

    private def getTag(RequestDef req) { req.headers()["X-Artik-Action"] }
    private def reqWithTag(String url, String tag) {
      new RequestDef(url).withHeaders(["X-Artik-Action": tag])
    }
    def syncReq() {
      return reqWithTag(endpoint + "all", "SYNC")
    }
    def getStateReq(selector) {
      return reqWithTag(endpoint + selector, "GET")
    }
    def setStateReq(selector, newState) {
      return reqWithTag(endpoint + selector + "/state", "UPDATE")
                .withMethod(HttpMethod.Put)
                .withBodyParams(newState)
    }
    def toggleReq(selector) {
      return reqWithTag(endpoint + selector + "/toggle", "TOGGLE")
                .withMethod(HttpMethod.Post)
    }
    def setEffectReq(selector, effect, params) {
      return reqWithTag(endpoint + selector + "/effects/" + effect, "EFFECT")
                .withMethod(HttpMethod.Post)
                .withBodyParams(params)
    }

    @Override
    def Or<RequestDef, Failure> fetch(Context ctx, RequestDef req, DeviceInfo info) {
      return new Good(req)
    }

    @Override
    def Or<List<Task>, Failure> onFetchResponse(Context ctx, RequestDef req, DeviceInfo info, Response res) {
      switch (res.status) {
        case HTTP_OK:
          switch (getTag(req)) {
            case "SYNC":
              def lights = slurper.parseText(res.content ?: "[]")
              def ids = ctx.findExtSubDeviceId(info).collect{it.toString()}.toSet()
              def events = lights.collect { light ->
                def id = light.get("id")
                def name = light.get("label")
                ids.remove(id)
                def lightType = "whiteLight"
                if (light.get("product").get("capabilities").get("has_color")) {
                  lightType = "colorLight"
                }
                new Event (ctx.now(), (ctx.parameters()["prefix"] ?: "") + name + ( ctx.parameters()["suffix"] ?: ""), EventType.createOrUpdateDevice, Option.apply(id), Option.apply(lightType))
              }
              lights.collect { light ->
                def id = light.get("id")
                events.push(new Event (ctx.now(), JsonOutput.toJson(enrichLightState(light, ctx)), EventType.data, Option.apply(id)))
              }
              ids.collect{ id -> events.push (new Event(ctx.now(), "", EventType.deleteDevice, Option.apply(id))) }
              return new Good(events)
            case "GET":
              def lights = slurper.parseText(res.content ?: "[]")
              def events = lights.collect { light ->
                def id = light.get("id")
                new Event (ctx.now(), JsonOutput.toJson(enrichLightState(light, ctx)), EventType.data, Option.apply(id))
              }
              return new Good(events)
            case "UPDATE":
              return new Good([])
            case "TOGGLE":
              return new Good([])
            case "EFFECT":
              return new Good([])
          }
          return new Bad(new Failure("[${info.did}] onFetchResponse response to unknown req " + req))
        case 207:
          switch (getTag(req)) {
            case "UPDATE":
            case "TOGGLE":
              def tasks = []
              def status = slurper.parseText(res.content ?: "[]")
              if (status["results"][0]["status"] == "ok") {
                tasks << getStateReq("id:" + status["results"][0]["id"]).withDelay(1000)
              }
              return new Good(tasks)
            case "EFFECT":
            case "SYNC":
            case "GET":
            default:
              return new Good([])
          }
      }
      return new Bad(new Failure("[${info.did}] onFetchResponse got status http status : ${res.status()}) with content: ${res.content()} after: ${req}"))
    }

    def getRGB(Map lightState, Context ctx) {
      float h = lightState.get("color").get("hue")
      float s = lightState.get("color").get("saturation")
      float l = lightState.get("brightness")

      def color = java.awt.Color.getHSBColor((float) (h / 360f), s, l)
      return ["r": color.getRed(), "g": color.getGreen(), "b": color.getBlue()]
    }

    def enrichLightState(Map lightState, Context ctx) {
      def state = lightState.get("connected") && (lightState.get("power") == "on")
      lightState["state"] = state ? "on" : "off"
      lightState["color"]["rgb"] = getRGB(lightState, ctx)
      return lightState
    }

}
