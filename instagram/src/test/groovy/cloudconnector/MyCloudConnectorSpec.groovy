package cloudconnector

import static java.net.HttpURLConnection.*

//import org.junit.Test
import utils.FakeContext
import static utils.Tools.*
import spock.lang.*
import org.scalactic.*
import scala.Option
import org.joda.time.format.DateTimeFormat
import org.joda.time.*
import cloud.artik.cloudconnector.api_v1.*
import groovy.json.JsonSlurper
import utils.FakeContext
import static utils.Tools.*

class MyCloudConnectorSpec extends Specification {

    def sut = new MyCloudConnector()
    def parser = new JsonSlurper()
    def ctx = new FakeContext()
    def apiEndpoint = "https://api.instagram.com/v1"
    def device = new DeviceInfo("deviceId", Empty.option(), new Credentials(AuthType.OAuth2, "", "1j0v33o6c5b34cVPqIiB_M2LYb_iM5S9Vcy7Rx7jA2630pK7HIjEXvJoiE8V5rRF", Empty.option(), Option.apply("bearer"), Empty.list(), Empty.option()), ctx.cloudId(), Empty.option())

    def "add accessToken into requests about device, with Credentials when phase is undef"() {
        when:
        def req0 = new RequestDef("$apiEndpoint/xxxxxx")
        def res = sut.signAndPrepare(ctx, req0, device, Phase.undef)
        then:
        res.isGood()
        res.get().queryParams["access_token"] ==  device.credentials.token
        //TODO test the "sig" hash with sample from the doc
        //	  https://instagram.com/developer/secure-api-requests/
        //	  Endpoint: /users/self
        //	  Parameters: access_token=fb2e77d.47a0479900504cb3ab4a1f626d174d2d
        //	  App Secret: 2a5be2f3eadd4e4eb0f2a2682423f749
        //	  With this example, the signature key/value should be:
        //	  HMACSHA256(2a5be2f3eadd4e4eb0f2a2682423f749, /users/self|access_token=fb2e77d.47a0479900504cb3ab4a1f626d174d2d)
        //	  =a6edca74ab34074efa5921695cca6aae892a339084467a5c8d533f5690ec33c3
    }

    def "retrieve external user id from user profile using subscribe"() {
        when:
        def reqs = sut.subscribe(ctx, device)
        def resp0 = new Response(HttpURLConnection.HTTP_OK, "application/x-www-form-urlencoded", readFile(this, "userInfo.json"))
        def res = sut.onSubscribeResponse(ctx, reqs.get()[0], device, resp0)
        then:
        reqs.isGood()
        reqs.get() ==  [new RequestDef("$apiEndpoint/users/self")]
        res.isGood()
        res.get() == Option.apply(device.withExtId("1574083"))
    }

    //https://instagram.com/developer/realtime/
    def "answer Pubsubhubub challenge flow."() {
        when:
        def challenge = "hgjhghvhvhj"
        def verifyToken = "yugtb§ètè!i!ç"
        def req = new RequestDef("").withQueryParams([
            "hub.mode" : "subscribe",
            "hub.challenge" : challenge,
            "hub.verify_token" : verifyToken
        ])
        def res = sut.onNotification(ctx, req)
        then:
        res.isGood()
        res.get().responseToCallback.get() == new Response(HttpURLConnection.HTTP_OK, "text/plain", challenge)
    }

    def "filter null in jsData"() {
        when:
        def json = """{"k0": {"k1": null, "k2": 1}, "k3": null}"""
        def res = sut.denull(parser.parseText(json))
        then:
        res == parser.parseText("""{"k0" :{"k2": 1}}""")
   }

   def "receive user notification, fetch Media information from cloud and queue appropriate messages."() {
      when:
      def dev1 = new DeviceInfo("did1", Option.apply("1234"), new Credentials(AuthType.OAuth2, "", "1j0v33o6c5b34cVPqIiB_M2LYb_iM5S9Vcy7Rx7jA2630pK7HIjEXvJoiE8V5rRF", Option.apply("A27CSzZXKf2EPB45lvLQyT56sZ80dXNtp_lA7lvZ6UIKAy94GNvW9g9aGmJtbl28"), Option.apply("bearer"), ["default", "activity", "location"], Option.apply(5183999L)), ctx.cloudId(), Empty.option())
      def dev2 = new DeviceInfo("did2", Option.apply("5678"), new Credentials(AuthType.OAuth2, "", "1j0v33o6c5b34cVPqIiB_M2LYb_iM5S9Vcy7Rx7jA2630pK7HIjEXvJoiE8V5rRF", Option.apply("A27CSzZXKf2EPB45lvLQyT56sZ80dXNtp_lA7lvZ6UIKAy94GNvW9g9aGmJtbl28"), Option.apply("bearer"), ["default", "activity", "location"], Option.apply(5183999L)), ctx.cloudId(), Empty.option())

      def requestApi1 = new RequestDef(apiEndpoint + "/users/self/media/recent").withQueryParams([
          "min_timestamp": "1297286521",
          "max_timestamp": "1297286542"
      ])
      def responseApi1 = new Response(HttpURLConnection.HTTP_OK, "application/json", readFile(this, "apiResponse1.json"))
      def requestApi2 = new RequestDef(apiEndpoint + "/users/self/media/recent").withQueryParams([
          "min_timestamp": "1297286526",
          "max_timestamp": "1297286547"
      ])
      def responseApi2 = new Response(HttpURLConnection.HTTP_OK, "application/json", readFile(this, "apiResponse2.json"))

      def event01 = new Event(1296251679000L, readFile(this, "queueMessage1_1.json"))
      def event02 = new Event(1279340983000L, readFile(this, "queueMessage2_1.json"))
      def event03 = new Event(1279340983000L, readFile(this, "queueMessage2_2.json"))

      def notificationRequest = new RequestDef("").withMethod(HttpMethod.Post).withContent(readFile(this, "callbackContent.json"), "application/json")
      def resNotif = sut.onNotification(ctx, notificationRequest)
      def res1 = sut.onFetchResponse(ctx, requestApi1, dev1, responseApi1)
      def res2 = sut.onFetchResponse(ctx, requestApi2, dev2, responseApi2)
      then:
      resNotif.get() == new NotificationResponse([
        new ThirdPartyNotification(new ByExtId(dev1.extId.get()), [ requestApi1 ]),
        new ThirdPartyNotification(new ByExtId(dev2.extId.get()), [ requestApi2 ])
      ])
      cmpTasks(res1.get(), [event01])
      cmpTasks(res2.get(), [event02, event03])
    }
}
