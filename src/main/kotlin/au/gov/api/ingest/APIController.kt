package au.gov.api.ingest

import au.gov.api.config.Config
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Klaxon
import com.beust.klaxon.Parser
import com.fasterxml.jackson.databind.ObjectMapper
import khttp.get
import khttp.structures.authorization.BasicAuthorization
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.env.Environment
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.io.File
import java.security.InvalidKeyException
import java.security.MessageDigest
import java.util.*
import javax.servlet.http.HttpServletRequest

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
            if (preview) {
                mf.assets.forEach { it.type = "${it.type}_preview" }
            }
            if (mf.metadata.id.isNullOrBlank() && !isManifestUnique(mf)) return "This resource already exists"

            var pipeOutputs = ExecuteManifest(mf)
            if (mf.metadata.id.isNullOrBlank()) {
                mf.metadata.id = pipeOutputs.first().toString()
            }
            repository.save(mf)
            logEvent(request, "Posted", "Manifest", mf.metadata.name!!, "Posted", ObjectMapper().writeValueAsString(mf))

            return ObjectMapper().writeValueAsString(pipeOutputs)
        } else {
            throw Unauthorised()
        }
    }

    @CrossOrigin
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/upload")
    fun uploadFile(request: HttpServletRequest,
                   @RequestParam(required = false, defaultValue = "") id: String,
                   @RequestParam fileName: String,
                   @RequestParam MIMEType: String,
                   @RequestBody x: ByteArray): String {
        if (isKeyValid(request)) {
            var md5 = hashString("MD5", x.toString())
            val key = getKeyFromRequest(request)
            if (id != "" && repository.findFileByKey(key).any { it.id.equals(id) }) {
                repository.deleteFile(id)
                md5 = id
            }
            var base64file = String(java.util.Base64.getEncoder().encode(x), Charsets.UTF_8)
            repository.saveFile(md5, fileName, MIMEType, key, base64file)
            return md5
        } else {
            throw InvalidKeyException()
        }
    }

    @CrossOrigin
    @ResponseStatus(HttpStatus.OK)
    @DeleteMapping("/upload")
    fun deleteFile(request: HttpServletRequest, @RequestParam id: String): Boolean {
        if (isKeyValid(request)) {
            val key = getKeyFromRequest(request)
            var success = false
            if (repository.findFileByKey(key).any { it.id.equals(id) }) {
                repository.deleteFile(id)
                success = true
            }
            return success
        } else {
            throw InvalidKeyException()
        }

    }

    @CrossOrigin
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/metadata")
    fun getMetaData(): String {

        return File("meta.json").readText()
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


    private fun hashString(type: String, input: String) =
            MessageDigest
                    .getInstance(type)
                    .digest(input.toByteArray())
                    .map { String.format("%02X", it) }
                    .joinToString(separator = "")

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
        pipe.buildPipeline(PipelineBuilder.AssetMechanism.All, repository)
        var outputs = pipe.executePipes()
        return outputs
    }

    private fun getKeyFromRequest(request: HttpServletRequest): String {
        if (environment.getActiveProfiles().contains("prod")) {
            val raw = request.getHeader("authorization")
            if (raw == null) return "Error";
            val apikey = String(Base64.getDecoder().decode(raw.removePrefix("Basic ")))

            return apikey.split(":")[0]
        } else {
            return "DEVKEY"
        }
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

    private fun isKeyValid(request: HttpServletRequest): Boolean {
       // if (environment.getActiveProfiles().contains("prod")) {
            val AuthURI = Config.get("AuthURI")

            // http://www.baeldung.com/get-user-in-spring-security
            val raw = request.getHeader("authorization")
            if (raw == null) return false;
            val apikey = String(Base64.getDecoder().decode(raw.removePrefix("Basic ")))

            val user = apikey.split(":")[0]
            val pass = apikey.split(":")[1]


            val authorisationRequest = get(AuthURI + "api/checkKey",
                    auth = BasicAuthorization(user, pass)
            )

            if (authorisationRequest.statusCode != 200) return false
            return authorisationRequest.text == "true"
        //}
        //return true
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
