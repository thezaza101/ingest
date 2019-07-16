package au.gov.api.ingest

import au.gov.api.config.Config
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Klaxon
import com.beust.klaxon.Parser
import khttp.structures.authorization.BasicAuthorization

abstract class Ingestor : PipeObject() {
    override val type: PipeType = PipeType.Ingestor
    var output : String = ""
    abstract fun setData(input:Any)
}

class ServiceDescriptionIngestor() : Ingestor() {
    var serviceDescription:ServiceDescription = ServiceDescription()
    override fun setData(input: Any) {
        serviceDescription = input as ServiceDescription
    }

    override fun execute() {

        val logURL = Config.get("BaseRepoURI")+"service"
        val parser = Parser()
        var payload = parser.parse(StringBuilder(Klaxon().toJsonString(serviceDescription))) as JsonObject
        var x = khttp.post(logURL,auth=GetAuth(),json = payload)
        output = x.text
        println("Status:"+x.statusCode)
    }

    fun GetAuth():BasicAuthorization {
        val eventAuth = System.getenv("IngestAuthKey")
        val eventAuthUser = eventAuth.split(":")[0]
        val eventAuthPass = eventAuth.split(":")[1]
        return BasicAuthorization(eventAuthUser, eventAuthPass)
    }
}

data class ServiceDescription(val id: String = "",val name: String = "", val description: String = "", val pages: List<String> = listOf(""),
                              var tags: MutableList<String> = mutableListOf(), var logo: String = "", var agency: String = "",
                              var ingestSrc: String = "", var space: String = "", var visibility: Boolean = false)