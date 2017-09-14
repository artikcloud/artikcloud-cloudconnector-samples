package cloudconnector

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
    def slurper = new JsonSlurper()
    def ctx = new FakeContext() {
        long now() { new DateTime(1970, 1, 17, 0, 0, DateTimeZone.UTC).getMillis() }
        List<String> findExtSubDeviceId(DeviceInfo deviceInfo) {
            ["123", "pojepgjjfgpj"]
        }
    }

    def device = new DeviceInfo("deviceId", Option.apply(null), new Credentials(AuthType.CustomAuth, "", "1j0v33o6c5b34cVPqIiB_M2LYb_iM5S9Vcy7Rx7jA2630pK7HIjEXvJoiE8V5rRF", Empty.option(), Option.apply("bearer"), ctx.scope(), Empty.option()), ctx.cloudId(), Empty.option())

    def "synchronizing devices and enriching status (state and colorRGB)"(){
        when:
        def res = sut.onFetchResponse(
                ctx,
                sut.syncReq(),
                device,
                new Response(200,  "application/json", readFile(this, "devices.json"))
        )
        then:
        res.isGood()
        cmpTasks(res.get(), [
                new Event(ctx.now(), "testManger", EventType.createOrUpdateDevice, Option.apply("pojepgjjfgpj"), Option.apply("colorLight")),
                new Event(ctx.now(), "testSalon", EventType.createOrUpdateDevice, Option.apply("lqshlghqlshglhlsdfglj"), Option.apply("colorLight")),
                new Event(ctx.now(), readFile(this, "pojepgjjfgpj_state.json"), EventType.data, Option.apply("pojepgjjfgpj")),
                new Event(ctx.now(), readFile(this, "lqshlghqlshglhlsdfglj_state.json"), EventType.data, Option.apply("lqshlghqlshglhlsdfglj")),
                new Event(ctx.now(), "", EventType.deleteDevice, Option.apply("123"))
        ])
    }
}
