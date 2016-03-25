package io.samsungsami.moves

import static java.net.HttpURLConnection.*

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
    def ctx = new FakeContext() {
        List<String> scope() {["default", "activity", "location"]}
        long now() { new DateTime(1970, 1, 17, 0, 0, DateTimeZone.UTC).getMillis() }
    }
    def extId = "23138311640030064"
    def apiEndpoint = "https://api.moves-app.com/api/1.1"
    def device = new DeviceInfo("deviceId", Option.apply(extId), new Credentials(AuthType.OAuth2, "", "1j0v33o6c5b34cVPqIiB_M2LYb_iM5S9Vcy7Rx7jA2630pK7HIjEXvJoiE8V5rRF", Empty.option(), Option.apply("bearer"), ctx.scope(), Empty.option()), ctx.cloudId(), Empty.option())

    // def "add accessToken into requests about device, with Credentials"() {
    //     //nothing to do
    // }

    def "build requests on notification"(){
        when:
        def req = new RequestDef("").withMethod(HttpMethod.Post).withContent(readFile(this, "apiNotification.json"), "application/json")
        def res = sut.onNotification(ctx, req)
        then:
        res.isGood()
        res.get() == new NotificationResponse([
            new ThirdPartyNotification(new ByExternalId(device.extId.get()), [
                new RequestDef(apiEndpoint + "/user/summary/daily/20121213").withQueryParams(["timeZone": "UTC"]),
                new RequestDef(apiEndpoint + "/user/summary/daily/20141213").withQueryParams(["timeZone": "UTC"])
            ]),
        ])
    }

    def "fetch summary 20121213"() {
        when:
        def req = new RequestDef(apiEndpoint + "/user/summary/daily/20121213").withQueryParams(["timeZone": "UTC"])
        def resp = new Response(HttpURLConnection.HTTP_OK, "application/json", readFile(this, "apiStoryLineResponse1.json"))
        def res = sut.onFetchResponse(ctx, req, device, resp)
        then:
        res.isGood()
        //20121213 at 23h59:59 = 1355443199 seconds
        def timestamp_20121213=1355443199999L
        def events = [
            new Event(timestamp_20121213, readFile(this, "events/summary/20121213/transport.json")),
            new Event(timestamp_20121213, readFile(this, "events/summary/20121213/underground.json")),
            new Event(timestamp_20121213, readFile(this, "events/summary/20121213/walking.json")),
            new Event(timestamp_20121213, readFile(this, "events/summary/20121213/walking_on_treadmill.json")),
            new Event(timestamp_20121213, readFile(this, "events/summary/20121213/zumba.json")),
            new Event(timestamp_20121213, """{"caloriesIdle":1000}""")
        ]
        cmpEvents(res.get(), events)
    }

    def "fetch summary 20121213"() {
        when:
        def req = new RequestDef(apiEndpoint + "/user/summary/daily/20141213").withQueryParams(["timeZone": "UTC"])
        def resp = new Response(HttpURLConnection.HTTP_OK, "application/json", readFile(this, "apiStoryLineResponse2.json"))
        def res = sut.onFetchResponse(ctx, req, device, resp)
        then:
        res.isGood()
        def timestamp_20141213=1418515199999L
        def events = [
            new Event(timestamp_20141213, readFile(this, "events/summary/20141213/walking.json")),
            new Event(timestamp_20141213, readFile(this, "events/summary/20141213/walking_on_treadmill.json")),
            new Event(timestamp_20141213, """{"caloriesIdle":1785}""")
        ]
        cmpEvents(res.get(), events)
    }

}
