package utils

import scala.Option
import com.samsung.sami.cloudconnector.api_v1.*

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
        [
            "endpoint":"http://127.0.0.1:9001/",
            "pushSecret":"<insert your push secret here>"
        ]
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
