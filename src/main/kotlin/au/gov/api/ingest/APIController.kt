package au.gov.api.ingest

import au.gov.api.config.Config
import au.gov.api.ingest.preview.DataImpl
import au.gov.api.ingest.preview.EngineImpl
import au.gov.api.ingest.preview.IngestImpl
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Klaxon
import com.beust.klaxon.Parser
import com.fasterxml.jackson.databind.ObjectMapper
import khttp.get
import khttp.structures.authorization.BasicAuthorization
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.env.Environment
import org.springframework.core.type.filter.AnnotationTypeFilter
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.*
import javax.servlet.http.HttpServletRequest
import kotlin.collections.HashMap

@RestController
class APIController {

    @ResponseStatus(HttpStatus.FORBIDDEN)
    class Unauthorised() : RuntimeException()

    @Autowired
    private lateinit var request: HttpServletRequest

    @Autowired
    private lateinit var environment: Environment

    @Autowired
    private lateinit var repository: IngestorRepository

    @CrossOrigin
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/manifest")
    fun setManifest(request: HttpServletRequest, @RequestBody mf: Manifest,
                    @RequestParam(required = false, defaultValue = "false") preview: Boolean): String {
        if (isAuthorisedToSaveService(request, mf.metadata.features.space!!)) {
            if (!preview) {

                if (mf.metadata.id.isNullOrBlank() && !isManifestUnique(mf)) return "This resource already exists"

                var pipeOutputs = ExecuteManifest(mf)
                if (mf.metadata.id.isNullOrBlank()) {
                    mf.metadata.id = pipeOutputs.first().toString()
                }
                repository.save(mf)
                logEvent(request, "Posted", "Manifest", mf.metadata.name!!, "Posted", ObjectMapper().writeValueAsString(mf))

                return ObjectMapper().writeValueAsString(pipeOutputs)

            } else {
                //previewManifest(mf)
                return "Not implimented"
            }
        } else {
            throw Unauthorised()
        }
    }

    @CrossOrigin
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/metadata")
    fun getMetaData(): String {
        var results = hashMapOf<String, HashMap<String, String>>()
        var data = hashMapOf<String, String>()
        var engines = hashMapOf<String, String>()
        var ingestors = hashMapOf<String, String>()


        var scannerData = ClassPathScanningCandidateComponentProvider(false)
        scannerData.addIncludeFilter(AnnotationTypeFilter(DataImpl::class.java))
        scannerData.findCandidateComponents("au.gov.api.ingest")
                .forEach {
                    data.put(it.beanClassName.split('.').last().replace("Data", ""),
                            annotationStringToJson(Class.forName(it.beanClassName).annotations.filter { it is DataImpl }.first().toString()))
                }


        var scannerEngines = ClassPathScanningCandidateComponentProvider(false)
        scannerEngines.addIncludeFilter(AnnotationTypeFilter(EngineImpl::class.java))
        scannerEngines.findCandidateComponents("au.gov.api.ingest")
                .forEach {
                    engines.put(it.beanClassName.split('.').last().replace("Engine", ""),
                            annotationStringToJson(Class.forName(it.beanClassName).annotations.filter { it is EngineImpl }.first().toString()))
                }

        var scannerIngestors = ClassPathScanningCandidateComponentProvider(false)
        scannerIngestors.addIncludeFilter(AnnotationTypeFilter(IngestImpl::class.java))
        scannerIngestors.findCandidateComponents("au.gov.api.ingest")
                .forEach {
                    ingestors.put(it.beanClassName.split('.').last().replace("Ingestor", ""),
                            annotationStringToJson(Class.forName(it.beanClassName).annotations.filter { it is IngestImpl }.first().toString()))
                }


        results.put("Data", data)
        results.put("Engine", engines)
        results.put("Ingestors", ingestors)

        return ObjectMapper().writeValueAsString(results).replace("\\", "").replace("\"{", "{").replace("}\"", "}")
    }

    fun annotationStringToJson(annotationString: String): String {
        var annotDetails = annotationString.split('.').last()
        annotDetails = annotDetails.removeRange(0, annotDetails.indexOf('(') + 1)
        annotDetails = annotDetails.take(annotDetails.length - 1)
        var output = "{"
        annotDetails.split(',').forEach {
            val both = it.split('=')
            output = "$output \"${both.first().trim()}\" : \"${both.last().trim()}\","
        }
        return "${output.take(output.length - 1)} }"
    }

    @CrossOrigin
    @ResponseStatus(HttpStatus.OK)  // 200
    @DeleteMapping("/manifest/{id}")
    fun deleteManifest(@PathVariable id: String, request: HttpServletRequest) {
        val manifest = repository.findById(id)

        if (isAuthorisedToSaveService(request, manifest.metadata.features.space!!)) {
            repository.delete(id)
            logEvent(request, "Deleted", "Manifest", manifest.metadata.id!!, "Deleted")
        }

        throw Unauthorised()
    }

    private fun isManifestUnique(mf: Manifest): Boolean {
        val existingManifests = repository.findAll()
        existingManifests
                .forEach {
                    if (it.metadata.name == mf.metadata.name &&
                            it.metadata.features.space == mf.metadata.features.space) return false
                }
        return true
    }

    private fun ExecuteManifest(mf: Manifest): MutableList<Any> {
        var pipe = PipelineBuilder(mf)
        pipe.buildPipeline()
        var outputs = pipe.executePipes()
        return outputs
    }

    private fun isAuthorisedToSaveService(request: HttpServletRequest, space: String): Boolean {
        if (environment.getActiveProfiles().contains("prod")) {
            val AuthURI = Config.get("AuthURI")

            // http://www.baeldung.com/get-user-in-spring-security
            val raw = request.getHeader("authorization")
            if (raw == null) return false;
            val apikey = String(Base64.getDecoder().decode(raw.removePrefix("Basic ")))

            val user = apikey.split(":")[0]
            val pass = apikey.split(":")[1]


            val authorisationRequest = get(AuthURI + "api/canWrite",
                    params = mapOf("space" to space),
                    auth = BasicAuthorization(user, pass)
            )
            if (authorisationRequest.statusCode != 200) return false
            return authorisationRequest.text == "true"
        }
        return true
    }

    data class Event(var key: String = "", var action: String = "", var type: String = "", var name: String = "", var reason: String = "", var content: String = "")

    private fun logEvent(request: HttpServletRequest, action: String, type: String, name: String, reason: String, content: String = "") {
        Thread(Runnable {
            try {
                print("Logging Event...")
                // http://www.baeldung.com/get-user-in-spring-security
                val raw = request.getHeader("authorization")
                val logURL = Config.get("LogURI") + "new"
                if (raw == null) throw RuntimeException()
                val user = String(Base64.getDecoder().decode(raw.removePrefix("Basic "))).split(":")[0]
                val parser: Parser = Parser()
                var eventPayload: JsonObject = parser.parse(StringBuilder(Klaxon().toJsonString(Event(user, action, type, name, reason, content)))) as JsonObject
                val eventAuth = System.getenv("LogAuthKey")
                val eventAuthUser = eventAuth.split(":")[0]
                val eventAuthPass = eventAuth.split(":")[1]
                var x = khttp.post(logURL, auth = BasicAuthorization(eventAuthUser, eventAuthPass), json = eventPayload)
                println("Status:" + x.statusCode)
            } catch (e: Exception) {
            }
        }).start()
    }

    fun writableSpaces(request: HttpServletRequest): List<String> {

        val AuthURI = Config.get("AuthURI")

        // http://www.baeldung.com/get-user-in-spring-security
        val raw = request.getHeader("authorization")
        if (raw == null) return listOf();
        val apikey = String(Base64.getDecoder().decode(raw.removePrefix("Basic ")))
        val user = apikey.split(":")[0]
        val pass = apikey.split(":")[1]


        val authorisationRequest = get(AuthURI + "api/spaces",
                auth = BasicAuthorization(user, pass)
        )
        if (authorisationRequest.statusCode != 200) return listOf()

        val spaces = mutableListOf<String>()

        for (i in 0..(authorisationRequest.jsonArray!!.length() - 1)) {
            val item = authorisationRequest.jsonArray!![i].toString()
            spaces.add(item)
        }

        return spaces


    }
}
