package utils

import scala.Option
import com.samsung.sami.cloudconnector.api_v1.*
import org.scalactic.*

class FakeContext implements Context {
    String clientId(){
        "clientId"
    }
    String clientSecret(){
        "clientSecret"
    }
    String cloudId(){
        "012345"
    }
    void debug(Object obj){
        println(obj)
    }
    long now(){
        10L
    }
    Map<String, String> parameters(){
        ["endpoint":"http://127.0.0.1:9001/"]
    }
    List<String> scope(){
        ["all"]
    }
    RequestDefTools requestDefTools() { new RequestDefTools(){
        java.util.Iterator<String> listFilesFromMultipartFormData(RequestDef req) { [] }
        Option<String> readFileFromMultipartFormData(RequestDef req, String key) { Option.apply(null)}
        List<String> getDataFromContent(String content, String key){ []}
    }}
    @Override
    Or<String, Failure> getOrCreateDevice(String samiToken, String deviceName, Option<String> externalId) {
        return new Good("d0")
    }
    @Override
    Or<Map<String, String>, Failure> getUserInfo(String samiToken) {
        return new Good(["name": "name"])
    }
    @Override
    Or<Map<String, String>, Failure> getUserId(String samiToken) {
        return new Good("uid")
    }
    @Override
    Or<List<ActionDef>, Failure> getLastActions(String samiToken, DeviceSelector deviceSelector, int limit) {
        return new Good([new ActionDef(Option.apply("sdid"), "ddid", 12345l, "actionName", "{}")])
    }
}
