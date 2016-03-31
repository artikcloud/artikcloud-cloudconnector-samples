package utils

import scala.Option
import cloud.artik.cloudconnector.api_v1.*

class FakeContext implements Context {
    String clientId(){
        "<insert your client id>"
    }
    String clientSecret(){
        "<insert your client secret>"
    }
    String cloudId(){
        "0123456"
    }
    void debug(Object obj){
        println(obj)
    }
    long now(){
        10L
    }
    List<String> scope(){
        ["all"]
    }
    Map<String, String> parameters(){
        ["endpoint":"https://api.twitter.com/1.1"]
    }
    RequestDefTools requestDefTools() { new RequestDefTools(){
        java.util.Iterator<String> listFilesFromMultipartFormData(RequestDef req) { [] }
        Option<String> readFileFromMultipartFormData(RequestDef req, String key) { Option.apply(null)}
        List<String> getDataFromContent(String content, String key){ []}
    }}
}
