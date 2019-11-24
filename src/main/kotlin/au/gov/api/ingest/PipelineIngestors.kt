package au.gov.api.ingest

import au.gov.api.config.Config
import au.gov.api.ingest.preview.IngestImpl
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Klaxon
import com.beust.klaxon.Parser
import khttp.structures.authorization.BasicAuthorization
import java.lang.Exception

abstract class Ingestor : PipeObject() {
    override val type: PipeType = PipeType.Ingestor
    var output : String = ""
    abstract fun setData(input:Any)
}

@IngestImpl("api_description","Posts the service description to the apigovau repository")
class ServiceDescriptionIngestor() : Ingestor() {
    var serviceDescription:ServiceDescription = ServiceDescription()
    override fun setData(input: Any) {
        serviceDescription = (input as Pair<String,Any>).second as ServiceDescription
    }

    override fun execute() {

        val logURL = Config.get("BaseRepoURI")+"service"
        val parser = Parser()
        var payload = parser.parse(StringBuilder(Klaxon().toJsonString(serviceDescription))) as JsonObject
        try {
            var x = khttp.post(logURL,auth=GetAuth(),json = payload)
            output = x.text
            println("Status:"+x.statusCode)
        } catch (e:Exception) {
            output = "Error updating repository"
            println(e.stackTrace)
        }

    }

    fun GetAuth():BasicAuthorization {
        val eventAuth = System.getenv("IngestAuthKey")
        val eventAuthUser = eventAuth.split(":")[0]
        val eventAuthPass = eventAuth.split(":")[1]
        return BasicAuthorization(eventAuthUser, eventAuthPass)
    }
}

@IngestImpl("api_description","Returns the service description as json")
class ServiceDescriptionIngestorPreview() : Ingestor() {
    var serviceDescription:ServiceDescription = ServiceDescription()
    override fun setData(input: Any) {
        serviceDescription = (input as Pair<String,Any>).second as ServiceDescription
    }
    override fun execute() {
        output = StringBuilder(Klaxon().toJsonString(serviceDescription)).toString()
    }
}

data class ServiceDescription(val id: String = "",val name: String = "", val description: String = "", val pages: List<String> = listOf(""),
                              var tags: MutableList<String> = mutableListOf(), var logo: String = "", var agency: String = "",
                              var ingestSrc: String = "", var space: String = "", var visibility: Boolean = false)