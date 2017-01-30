package utils

import scala.Option
import cloud.artik.cloudconnector.api_v1.*
import org.joda.time.*
import cloudconnector.MyCloudConnector

class FakeContext extends cloud.artik.cloudconnector.testkit.FakeContext {
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
        DateTime.parse("Fri, 1 Jan 2016 09:45:00", MyCloudConnector.timestampFormat).getMillis()
    }
    Map<String, String> parameters(){
        ["endpoint":"http://127.0.0.1:9001"]
    }
    List<String> scope(){
        ["all"]
    }
    RequestDefTools requestDefTools() { new RequestDefTools(){
        java.util.Iterator<String> listFilesFromMultipartFormData(RequestDef req) { [] }
        Option<String> readFileFromMultipartFormData(RequestDef req, String key) { Option.apply(null)}
        List<String> getDataFromContent(String content, String key){ []}
    }}
}
