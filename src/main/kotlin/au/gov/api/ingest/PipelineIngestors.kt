package au.gov.api.ingest

import au.gov.api.config.Config
import au.gov.api.ingest.preview.IngestImpl
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Klaxon
import com.beust.klaxon.Parser
import com.fasterxml.jackson.databind.ObjectMapper
import khttp.structures.authorization.BasicAuthorization
import java.security.KeyException
import java.lang.Exception

abstract class Ingestor : PipeObject() {
    override val type: PipeType = PipeType.Ingestor
    var output: String = ""
    abstract fun setData(input: Any)
}

@IngestImpl("api_description", "Posts the service description to the apigovau repository")
class ServiceDescriptionIngestor() : Ingestor() {
    var serviceDescription: ServiceDescription = ServiceDescription()
    override fun setData(input: Any) {
        serviceDescription = (input as Pair<String, Any>).second as ServiceDescription
    }

    override fun execute() {
        val logURL = Config.get("BaseRepoURI") + "service"
        val parser = Parser()
        var payload = parser.parse(StringBuilder(Klaxon().toJsonString(serviceDescription))) as JsonObject
        var x = khttp.post(logURL, auth = GetAuth(), json = payload)
        output = x.text
        println("${serviceDescription.name}:${x.statusCode}")

        //Update the manifest id for new services
        if (x.statusCode==201) {
            if (!manifest.metadata.id.isNullOrEmpty()) {
                if (x.text != manifest.metadata.id) {
                    var delUri = "$logURL/${x.text}"
                    var del = khttp.delete(delUri, auth = GetAuth())
                    throw KeyException("ID mismatch between manifest and repository")
                }
            }
            manifest.metadata.id = x.text
            val manifestURI = Config.get("Ingest") + "manifest?update=true"

            var y = khttp.post(manifestURI, auth = GetAuth(), json = parser.parse(StringBuilder(Klaxon().toJsonString(manifest))) as JsonObject)
            if (y.statusCode!=200) {
                println("Failed to update id for ${x.text}")
            } else {
                println("Updated id for ${x.text}")
            }
        }
    }

    fun GetAuth(): BasicAuthorization {
        val eventAuth = System.getenv("IngestAuthKey")
        val eventAuthUser = eventAuth.split(":")[0]
        val eventAuthPass = eventAuth.split(":")[1]
        return BasicAuthorization(eventAuthUser, eventAuthPass)
    }
}

@IngestImpl("api_description", "Returns the service description as json")
class ServiceDescriptionIngestorPreview() : Ingestor() {
    var serviceDescription: ServiceDescription = ServiceDescription()
    override fun setData(input: Any) {
        serviceDescription = (input as Pair<String, Any>).second as ServiceDescription
    }

    override fun execute() {
        output = StringBuilder(Klaxon().toJsonString(serviceDescription)).toString()
    }
}

@IngestImpl("markdown", "Returns the markdown text")
class GetMarkdown() : Ingestor() {
    var markdownRaw = ""
    override fun setData(input: Any) {
        markdownRaw = (input as Pair<String, Any>).second as String
    }

    override fun execute() {
        output = markdownRaw
    }
}

data class ServiceDescription(val id: String = "", val name: String = "", val description: String = "", val pages: List<String> = listOf(""),
                              var tags: MutableList<String> = mutableListOf(), var logo: String = "", var agency: String = "",
                              var ingestSrc: String = "", var space: String = "", var visibility: Boolean = false)