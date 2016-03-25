package utils

import scala.Option
import cloud.artik.cloudconnector.api_v1.*

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
            "pushSecret":"BVM5AI3ROUKCJI0WC1O33CD22R0MOV3KZG4VQYPDXOXB2IR3"
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
