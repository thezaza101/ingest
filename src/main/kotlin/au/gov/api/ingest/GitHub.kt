/*package au.gov.api.ingest.Service

import com.beust.klaxon.JsonObject
import java.net.URL
import com.beust.klaxon.Parser
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component



@Component
class GitHub{


    private val gitHubApiBaseUri = "https://api.github.com"

    constructor(){}

    companion object{

        @JvmStatic
        fun getRawURI(actualURL:String):String{
            //val actualURI = "https://github.com               /apigovau/api-gov-au-definitions/blob/master/api-documentation.md"
            //val rawURIi   = "https://raw.githubusercontent.com/apigovau/api-gov-au-definitions/master/api-documentation.md"
            
            return actualURL.replace("github.com","raw.githubusercontent.com").replace("/blob/master/","/master/")
        }

        @JvmStatic
        fun getTextOfFlie(uri:String):String{
            return URL(getRawURI(uri)).readText() 
        }

        @JvmStatic
        fun getUserGitHubUri(uri:String):String{
            val startPos = uri.indexOf("com/",0,true)+4
            val endPos = uri.indexOf("/",startPos,true)
            val result = uri.substring(startPos,endPos)
            return result
        }

        @JvmStatic
        fun getRepoGitHubUri(uri:String):String{
            val userName = getUserGitHubUri(uri)
            val startPos = uri.indexOf(userName+"/",0,true) + userName.length+1
            val endPos = uri.indexOf("/",startPos,true)
            val result = uri.substring(startPos,endPos)
            return result
        }
    }
}*/
