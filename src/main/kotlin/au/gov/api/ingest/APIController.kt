package au.gov.api.ingest

import au.gov.api.config.Config
import au.gov.api.ingest.helpers.PaginationResult
import au.gov.api.ingest.helpers.URLHelper
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

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    class InvallidRequest(override val message: String?) : java.lang.Exception()

    @ResponseStatus(HttpStatus.NOT_FOUND)
    class NoContentFound(override val message: String?) : java.lang.Exception()

    @CrossOrigin
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/manifest", produces = arrayOf("application/json"))
    fun setManifest(request: HttpServletRequest, @RequestBody mf: Manifest,
                    @RequestParam(required = false, defaultValue = "false") preview: Boolean,
                    @RequestParam(required = false, defaultValue = "false") update: Boolean): String {
        if (isAuthorisedToSaveService(request, mf.metadata.features.space!!)) {
            if (preview) {
                mf.assets.forEach { it.type = "${it.type}_preview" }
            }
            if (update) {
                repository.save(mf)
                return "Manifest updated"
            }
            if (mf.metadata.id.isNullOrBlank() && !isManifestUnique(mf)) return "This resource already exists"

            var pipeOutputs = ExecuteManifest(mf)
            if (mf.metadata.id.isNullOrBlank()) {
                mf.metadata.id = pipeOutputs.first().toString()
            }

            if (!preview) {
                repository.save(mf)
                println("Saved Manifest for \"${mf.metadata.name}\" with id: ${mf.metadata.id}")
            }

            logEvent(request, "Posted", "Manifest", mf.metadata.name!!, "Posted", ObjectMapper().writeValueAsString(mf))

            return pipeOutputs.last() as String
        } else {
            throw Unauthorised()
        }
    }


    @CrossOrigin
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/listFiles")
    fun listFile(request: HttpServletRequest, @RequestParam(defaultValue = "20") size: Int,
                 @RequestParam(defaultValue = "1") page: Int,
                 @RequestParam(required = false, defaultValue = "true") sort: Boolean): PaginationResult<FileData> {
        val key = getKeyFromRequest(request)
        var fullList = repository.findFileByKey(key)

        return PaginationResult(getFilesHATEOS(fullList, sort, size, page), URLHelper().getURL(request), fullList.count())
    }

    fun getFilesHATEOS(files: List<FileData>, sort: Boolean = false, size: Int = 10, page: Int = 1): List<FileData> {
        var completeList = files.toMutableList()
        if (sort) {
            completeList = completeList.sortedBy { it.timestamp }.toMutableList()
        }


        var pageStart = page - 1
        if (page > 1) pageStart = size * pageStart
        var pageEnd = pageStart + size

        if (pageStart > completeList.count()) {
            if (page > 1) {
                throw InvallidRequest("No content found at page $page")
            } else {
                throw NoContentFound("This user has not uploaded any files")
            }
        }
        if (pageEnd > completeList.count()) {
            if (page > 1) {
                if ((pageEnd - pageStart) <= completeList.count()) {
                    pageEnd = completeList.count()
                } else {
                    throw InvallidRequest("No content found at page $page")
                }
            } else {
                if (pageStart == 0) {
                    pageEnd = completeList.count()
                } else {
                    throw InvallidRequest("No content found at page $page")
                }
            }
        }

        return completeList.subList(pageStart, pageEnd)
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
    @GetMapping("/metadata", produces = arrayOf("application/json"))
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
        if (environment.getActiveProfiles().contains("prod")) {
            val AuthURI = Config.get("AuthURI")

            // http://www.baeldung.com/get-user-in-spring-security
            val raw = request.getHeader("authorization")
            if (raw == null) return false;
            val apikey = String(Base64.getDecoder().decode(raw.removePrefix("Basic ")))

            val user = apikey.split(":")[0]
            val pass = apikey.split(":")[1]

            println("Attempting to validate key...")
            val authorisationRequest = get(AuthURI + "api/checkKey",
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
                println("Logged event:" + x.statusCode)
            } catch (e: Exception) {
                println("Failed to log event: ${e}")
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
