package io.samsungsami.twitter

import static java.net.HttpURLConnection.*

//import org.junit.Test
import spock.lang.*
import org.scalactic.*
import scala.Option
import org.joda.time.format.DateTimeFormat
import org.joda.time.*
import cloud.artik.cloudconnector.api_v1.*
import utils.FakeContext
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import static utils.Tools.*

class MyCloudConnectorSpec extends Specification {

    def sut = new MyCloudConnector()
    def ctx = new FakeContext()
    def parser = new JsonSlurper()

    def "reject unkown action"() {
        when:
        def action = new ActionDef(Option.apply("sdid"), "ddid", System.currentTimeMillis(), "bar", '{"value":"foo"}')
        def fakeDevice = new DeviceInfo(
            "ddid", 
            Option.apply("extId"), 
            new Credentials(AuthType.OAuth2, "", "", Empty.option(), Option.apply("bearer"), ctx.scope(), Empty.option()), ctx.cloudId(), Empty.option()
        )
        def actionRes = sut.onAction(ctx, action, fakeDevice)

        then:
        actionRes.isBad()
    }

    def "test function generateTwitterAuthorization"() {
        when:
        def oauthParams = parser.parseText(readFile(this, "oauthParams.json"))
        def twitterAuthorization = sut.generateTwitterAuthorization(oauthParams)
        then:
        //for twitter, signature should be URL-encoded
        twitterAuthorization == 'OAuth oauth_consumer_key="<insert your client id>", oauth_nonce="a6f0df7089eade26d2c965e83b807414", oauth_signature="NpzMqta0hlTMnP1irJ91l4eRlOU%3D", oauth_signature_method="HMAC-SHA1", oauth_timestamp="1459410365", oauth_token="<insert your user token>", oauth_version="1.0"'
    }

    def "test function addTwitterHeader"() {
        when:
        def req = new RequestDef("${ctx.parameters().endpoint}/statuses/update.json")
                            .withMethod(HttpMethod.Post)
                            .withContent("status=Maybe%20he%27ll%20finally%20find%20his%20keys.%20%23peterfalk", "application/x-www-form-urlencoded")
        def info = parser.parseText(readFile(this, "deviceInfo.json"))
        def res = sut.addTwitterHeader(
            req,
            ctx.clientId(),
            ctx.clientSecret(),
            info.token,
            info.tokenSecret
            )
        def expectedRes = new RequestDef("https://api.twitter.com/1.1/statuses/update.json")
                            .withMethod(HttpMethod.Post)
                            .withHeaders(["Authorization": sut.generateTwitterAuthorization(
                                    sut.getAuthParams(
                                        req,
                                        ctx.clientId(),
                                        ctx.clientSecret(),
                                        info.token,
                                        info.tokenSecret
                                        )
                                    )
                                ])
                            .withContent("status=Maybe%20he%27ll%20finally%20find%20his%20keys.%20%23peterfalk", "application/x-www-form-urlencoded")
        then:
        res == expectedRes
    }

    def "test function signAndPrepare"() {
        when:
        def req = new RequestDef("${ctx.parameters().endpoint}/statuses/update.json")
                            .withMethod(HttpMethod.Post)
                            .withContent("status=Maybe%20he%27ll%20finally%20find%20his%20keys.%20%23peterfalk", "application/x-www-form-urlencoded")
        def info = parser.parseText(readFile(this, "deviceInfo.json"))
        def device = new DeviceInfo("deviceId", 
                                    Empty.option(), 
                                    new Credentials(
                                        AuthType.OAuth1, 
                                        "<insert your user token secret>", 
                                        "<insert your user token>", 
                                        Empty.option(), 
                                        Option.apply("bearer"), 
                                        Empty.list(), 
                                        Empty.option()
                                    ), 
                                    ctx.cloudId(), 
                                    Empty.option()
                                    )
        def res = sut.signAndPrepare(ctx, req, device, Phase.undef)
        then:
        res.isGood()
    }

    def "send action to cloud when receiving ARTIK Cloud action"() {
        when:
        def action = new ActionDef(Option.apply("sdid"), "ddid", System.currentTimeMillis(), "updateStatus", '{"status":"Maybe he\'ll finally find his keys. #peterfalk"}')
        def fakeDevice = new DeviceInfo(
            "ddid", 
            Option.apply("extId"), 
            new Credentials(AuthType.OAuth1, "", "", Empty.option(), Option.apply("bearer"), ctx.scope(), Empty.option()), ctx.cloudId(), Empty.option()
        )
        def actionRes = sut.onAction(ctx, action, fakeDevice)

        then:
        actionRes.isGood()
        actionRes.get() == new ActionResponse([
            new ActionRequest(
                [
                    new RequestDef("${ctx.parameters().endpoint}/statuses/update.json")
                        .withMethod(HttpMethod.Post).withContentType("application/x-www-form-urlencoded")
                        .withBodyParams([status: "Maybe he\'ll finally find his keys. #peterfalk"])
                ]
            )    
        ])
    }


}
