package io.samsungsami.twitter

import static java.net.HttpURLConnection.*

import org.scalactic.*
import org.joda.time.format.DateTimeFormat
import org.joda.time.*
import groovy.transform.CompileStatic
import groovy.transform.ToString
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import cloud.artik.cloudconnector.api_v1.*
import org.joda.time.format.ISODateTimeFormat
import scala.Option
import java.net.URLEncoder

//2 import sections just for supporting getAuthParams 
import com.ning.http.client.Param;
import com.ning.http.client.oauth.*;
import com.ning.http.client.uri.Uri;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

//@CompileStatic
class MyCloudConnector extends CloudConnector {
    static mdateFormat = DateTimeFormat.forPattern('yyyy-MM-dd HH:mm:ss').withZoneUTC()
    static final CT_JSON = 'application/json'
    static final allowedKeys = [
        "status", 
        "display_coordinates", "lat", "long"
    ]

    JsonSlurper slurper = new JsonSlurper()
    
    def getAuthParams(RequestDef requestDef, String key, String secret, String token, String tokenSecret) throws UnsupportedEncodingException {
        ConsumerKey consumerKey = new ConsumerKey(key, secret);
        RequestToken accessTokens = new RequestToken(token, tokenSecret);
        OAuthSignatureCalculator oAuthSignatureCalculator =  new OAuthSignatureCalculator(consumerKey, accessTokens);
        // for Twitter, timestamp is in Second, just change 1 line following :
        Long ts = (int)(System.currentTimeMillis() / 1000);
        String nonce = String.valueOf(ts);
        List<Param> queryParams = new ArrayList<Param>(requestDef.queryParams().size());
        for (java.util.Map.Entry<String,String> s : requestDef.queryParams().entrySet()) {
            queryParams.add(new Param(s.getKey(), s.getValue()));
        }
        List<Param> formParams = new ArrayList<Param>(requestDef.bodyParams().size());
        for (java.util.Map.Entry<String,String> s : requestDef.bodyParams().entrySet()) {
            formParams.add(new Param(s.getKey(), s.getValue()));
        }
        Uri uri = Uri.create(requestDef.url());
        String signature = oAuthSignatureCalculator.calculateSignature(requestDef.method().toString().toUpperCase(), uri, ts, nonce, formParams, queryParams);
        Map<String, String> authParams = new HashMap<String, String>();
        authParams.put("oauth_consumer_key", URLEncoder.encode(consumerKey.getKey(), "UTF-8"));
        authParams.put("oauth_nonce", nonce);
        authParams.put("oauth_signature", signature);
        authParams.put("oauth_signature_method", "HMAC-SHA1");
        authParams.put("oauth_timestamp", ts.toString());
        authParams.put("oauth_token", URLEncoder.encode(accessTokens.getKey(), "UTF-8"));
        authParams.put("oauth_version", "1.0");
        return authParams;
    }
    
    @Override
    def Or<RequestDef, Failure> signAndPrepare(Context ctx, RequestDef req, DeviceInfo info, Phase phase){
        return new Good(
            addTwitterHeader(
                req,
                ctx.clientId(),
                ctx.clientSecret(),
                info.credentials().token(),
                info.credentials().secret()
            ) 
        )
    }

    // -----------------------------------------
    // CALLBACK
    // -----------------------------------------

    @Override
    Or<List<Event>, Failure> onFetchResponse(Context ctx, RequestDef req, DeviceInfo info, Response res) {
        ctx.debug(res.content)
        switch (res.status) {
            case HTTP_OK:
                def content = res.content.trim()
                ctx.debug(content)
                if (content == '' || content == 'OK') {
                    ctx.debug("ignore response valid respond: '${res.content}'")
                    return new Good([])
                } else if (res.contentType.startsWith(CT_JSON)) {
                    def json = slurper.parseText(content)
                    def ts = (json.datetime) ? DateTime.parse(json.datetime, mdateFormat).millis : ctx.now()
                    //return new Good([new Event(ts, JsonOutput.toJson(slurper.parseText(json.message)))])
                    return new Good([new Event(ts, json.message)])
                }
                return new Bad(new Failure("unsupported response ${res} ... ${res.contentType} .. ${res.contentType.startsWith(CT_JSON)}"))
            default:
                return new Bad(new Failure("http status : ${res.status} is not OK (${HTTP_OK})"))
        }
    }

    @Override
    Or<ActionResponse, Failure> onAction(Context ctx, ActionDef action, DeviceInfo info) {
        def paramsAsJson = slurper.parseText(action.params)
        if (paramsAsJson?.status == null) {
            return new Bad(new Failure("Missing field 'status' in action parameters ${paramsAsJson}"))
        }
        def tweetParams = filterByAllowedKeys(paramsAsJson, allowedKeys)

        switch (action.name) {
            case "updateStatus":   
                def req = stringifyAndSend(tweetParams, "${ctx.parameters().endpoint}/statuses/update.json") 
                return new Good(new ActionResponse([new ActionRequest([req])]))
            case "updateStatusWithGeolocation": 
                tweetParams << [display_coordinates: true] 
                def req = stringifyAndSend(tweetParams, "${ctx.parameters().endpoint}/statuses/update.json")
                return new Good(new ActionResponse([new ActionRequest([req])]))
            default:        
                return new Bad(new Failure("Unknown action: ${action.name}"))
        }
    }

    def stringifyAndSend(Map params, String url) {
        def paramsStringified = params.collectEntries { key, value -> [key, value.toString()] }
        def req = new RequestDef(url)
            .withContentType("application/x-www-form-urlencoded")
            .withMethod(HttpMethod.Post).withBodyParams(paramsStringified) 
        return req
    }

    def generateTwitterAuthorization(Map params) {
        def DST = 'OAuth oauth_consumer_key="' + params.oauth_consumer_key +
                 '", oauth_nonce="' + params.oauth_nonce + 
                 '", oauth_signature="' + URLEncoder.encode(params.oauth_signature, "UTF-8") + 
                 '", oauth_signature_method="HMAC-SHA1' + 
                 '", oauth_timestamp="' + params.oauth_timestamp + 
                 '", oauth_token="' + params.oauth_token + 
                 '", oauth_version="1.0"'
        return DST
    }

    def addTwitterHeader(RequestDef requestDef, String key, String secret, String token, String tokenSecret) {
        def oAuthParams = getAuthParams(requestDef, key, secret, token, tokenSecret)
        return requestDef.addHeaders([
            Authorization: generateTwitterAuthorization(oAuthParams)
        ])
    }

    // copy-pasted from sami-cloudconnector-samples/foursquare/src/main/groovy/io/samsungsami/foursquare/MyCloudConnector.groovy
    def filterByAllowedKeys(obj, allowedKeys) {
        applyToMessage(obj, { k, v ->
            allowedKeys.contains(k)? [(k): (v)]: [:]
        })
    }

    // copy-pasted from sami-cloudconnector-samples/open-weather-map/src/main/groovy/io/samsungsami/openWeatherMap/MyCloudConnector.groovy -> transformJson
    // applyToMessage(obj, f) will remove all empty values
    def applyToMessage(message, f) {
        if (message instanceof java.util.Map) {
            message.collectEntries { k, v ->
                if (v != null) {
                    def newV = applyToMessage(v, f)
                    if (newV != [:]) {
                        f(k, newV)
                    } else {
                        [:]
                    }
                } else {
                    [:]
                }
            }
        } else if (message instanceof java.util.Collection) {
            java.util.Collection newList = message.collect { item ->
                applyToMessage(item, f)
            }
            (newList.isEmpty()) ? [:] : newList
        } else {
            message
        }
    }
    
}
