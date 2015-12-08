package io.samsungsami.misfit

import static java.net.HttpURLConnection.*

import utils.FakeContext
import static utils.Tools.*
import spock.lang.*
import org.scalactic.*
import scala.Option
import org.joda.time.format.DateTimeFormat
import org.joda.time.*
import com.samsung.sami.cloudconnector.api.*
import groovy.json.JsonSlurper
import utils.FakeContext
import static utils.Tools.*

class MyCloudConnectorSpec extends Specification {

    def sut = new MyCloudConnector()
    def parser = new JsonSlurper()
    def ctx = new FakeContext() {
        List<String> scope() {["public", "tracking", "sessions", "sleeps"]}
        long now() { new DateTime(1970, 1, 17, 0, 0, DateTimeZone.UTC).getMillis() }
    }
    def extId = "externalID054"
    def apiEndpoint = "https://api.misfitwearables.com/move/resource/v1/user/" + extId
    def device = new DeviceInfo("deviceId", Option.apply(extId), new Credentials(AuthType.OAuth2, "", "1j0v33o6c5b34cVPqIiB_M2LYb_iM5S9Vcy7Rx7jA2630pK7HIjEXvJoiE8V5rRF", Empty.option(), Option.apply("bearer"), ctx.scope(), Empty.option()), ctx.cloudId(), Empty.option())

    def "add accessToken into requests about device, with Credentials if Phase is undef"() {
        when:
        def req0 = new RequestDef("$apiEndpoint/xxxxxx")
        def res = sut.signAndPrepare(ctx, req0, device, Phase.undef)
        then:
        res.isGood()
        res.get().headers["access_token"] ==  device.credentials.token
    }

    //https://build.misfit.com/docs/references#APIReferences-NotificationServer
    def "confirm subscription"() {
        when:
        def req = new RequestDef("").withMethod(HttpMethod.Post)
            .withContent(readFile(this, "subscribtionConfirmationRequest.json"), "text/plain")
            .withHeaders(["User-Agent": "Amazon Simple Notification Service Agent"])
        def res = sut.onNotification(ctx, req)
        then:
        res.isGood()
        res.get() == new NotificationResponse([new ThirdPartyNotification(Empty.deviceSelector(), [new RequestDef("http://SecreturlToConfirmSubscription")])])
    }

    def "build requests on notification"(){
        when:
        def req = new RequestDef("").withMethod(HttpMethod.Post).withContent(readFile(this, "apiNotification.json"), "application/json")
        def res = sut.onNotification(ctx, req)
        then:
        res.isGood()
        res.get() == new NotificationResponse([
            new ThirdPartyNotification(new ByExternalDeviceId(device.extId.get()), [
                new RequestDef(apiEndpoint + "/activity/goals/abcdef12345"),
                new RequestDef(apiEndpoint + "/activity/summary").withQueryParams(["start_date": "2014-01-01", "end_date": "2014-01-01", "detail": "true"]),
                new RequestDef(apiEndpoint + "/device")
            ]),
            new ThirdPartyNotification(new ByExternalDeviceId(device.extId.get()), [
                new RequestDef(apiEndpoint + "/activity/goals/abcdef123456"),
                new RequestDef(apiEndpoint + "/activity/summary").withQueryParams(["start_date": "2014-02-03", "end_date": "2014-02-03", "detail": "true"]),
                new RequestDef(apiEndpoint + "/device")
            ]),
            new ThirdPartyNotification(new ByExternalDeviceId(device.extId.get()), [
                new RequestDef(apiEndpoint + "/activity/sleeps/12345sleep")
            ])
        ])
    }

    def "fetch goals 2"() {
        when:
        def request2 = new RequestDef(apiEndpoint + "/activity/goals/abcdef12345")
        def response2 = new Response(HttpURLConnection.HTTP_OK, "application/json", readFile(this, "apiResponse2.json"))

        def event02 = new Event(1381017599999L, readFile(this, "apiResponse2event01.json"))
        def res2 = sut.onFetchResponse(ctx, request2, device, response2)
        then:
        res2.isGood()
        cmpEvents(res2.get(), [event02])
    }

    def "fetch goals 3"() {
        when:
        def request3 = new RequestDef(apiEndpoint + "/activity/goals/abcdef123456")
        def response3 = new Response(HttpURLConnection.HTTP_OK, "application/json; charset=utf-8", readFile(this, "apiResponse3.json"))

        def event03 = new Event(1381017599999L, readFile(this, "apiResponse3event01.json"))
        def res3 = sut.onFetchResponse(ctx, request3, device, response3)
        then:
        res3.isGood()
        cmpEvents(res3.get(), [event03])
    }

    def "fetch sleeps"() {
        when:
        def request4 = new RequestDef(apiEndpoint + "/activity/sleeps/12345sleep")
        def response4 = new Response(HttpURLConnection.HTTP_OK, "application/json; charset=utf-8", readFile(this, "apiResponse4.json"))

        def event0401 = new Event(1400516814000L, readFile(this, "apiResponse4event01.json"))
        def event0402 = new Event(1400516814000L, readFile(this, "apiResponse4event02.json"))
        def event0403 = new Event(1400518762000L, readFile(this, "apiResponse4event03.json"))
        def res4 = sut.onFetchResponse(ctx, request4, device, response4)
        then:
        res4.isGood()
        cmpEvents(res4.get(), [event0401, event0402, event0403])
    }
    
    def "fetch summary"(){
        when:
        def request5 = new RequestDef(apiEndpoint + "/activity/summary").withQueryParams(["start_date": "1970-01-17", "end_date": "1970-01-17", "detail": "true"])
        def response5 = new Response(HttpURLConnection.HTTP_OK, "application/json; charset=utf-8", readFile(this, "apiResponse5.json"))
        def event05 = new Event(1383695999999L, readFile(this, "apiResponse5event01.json"))
        def res5 = sut.onFetchResponse(ctx, request5, device, response5)
        then:
        res5.isGood()
        cmpEvents(res5.get(), [event05])
    }

    def "fetch device"(){
        when:
        def request = new RequestDef(apiEndpoint + "/device")
        def response = new Response(HttpURLConnection.HTTP_OK, "application/json; charset=utf-8", readFile(this, "apiResponseDevice01.json"))
        def event01 = new Event(1433690072000L, readFile(this, "apiResponseDevice01event01.json"))
        def res = sut.onFetchResponse(ctx, request, device, response)
        then:
        res.isGood()
        cmpEvents(res.get(), [event01])
    }
}
