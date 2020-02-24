package au.gov.api.ingest

import au.gov.api.config.Config
import au.gov.api.ingest.converters.models.ObjectDocument
import au.gov.api.ingest.converters.swaggerToMarkdown.SwaggerToMarkdownConverter
import au.gov.api.ingest.preview.EngineImpl
import com.fasterxml.jackson.databind.ObjectMapper
import org.ow2.easywsdl.wsdl.WSDLFactory
import org.ow2.easywsdl.wsdl.api.Description
import org.springframework.core.io.ClassPathResource
import org.springframework.util.FileCopyUtils
import org.xml.sax.InputSource
import java.io.ByteArrayInputStream
import java.io.StringReader
import java.nio.charset.StandardCharsets
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory

abstract class Engine : PipeObject() {
    var inputIds: List<String>? = null
    var inputData = ""
    var outputId = ""
    var config: HashMap<String, String>? = null
    open var output: String? = null

    override val type: PipeType = PipeType.Engine

    fun setInputNames(names: List<String>) {
        inputIds = names
    }

    fun setOutputName(name: String) {
        outputId = name
    }

    fun setConfigration(conf: HashMap<String, String>?) {
        config = conf
    }

    fun setAllConfig(inNames: List<String>, outName: String, engConf: HashMap<String, String>?) {
        setInputNames(inNames)
        setOutputName(outName)
        setConfigration(engConf)
    }

    open fun setData(vararg input: Any) {
        //inputData = (input.last() as Pair<String, Any>).second as String
        inputData = (input.filter { inputIds!!.contains((it as Pair<String, Any>).first) }
                .first() as Pair<String, Any>).second.toString()
    }

    open fun getOutput(): Any {
        when (output == null) {
            true -> {
                execute()
                return output!!
            }
            false -> return output!!
        }
    }


}

@EngineImpl("markdown",
        "markdown",
        "Removes 'front matter' from markdown files")
class StripFrontMatterEngine : Engine() {
    override fun execute() {
        var contentStart = inputData.indexOf("---", inputData.indexOf("---") + 3)
        output = inputData.substring(contentStart + 3).trim()
    }
}

@EngineImpl("markdown",
        "api_description",
        "Converts a markdown document to a Service design")
class SingleMarkdownToServiceDesignEngine : Engine() {
    var outputSd: ServiceDescription? = null

    override fun execute() {
        val id = manifest.metadata.id ?: ""
        val name = manifest.metadata.name ?: ""
        val description = manifest.metadata.description ?: ""
        val pages = getSDPages()
        val logo = manifest.metadata.logo ?: ""
        val space = manifest.metadata.features.space ?: ""
        var agencyAcr = manifest.metadata.features.agencyAcr ?: ""
        var insrc = ""
        if (manifest.assets.size > manifest.assetIdx) {
            val asset = manifest.assets[manifest.assetIdx]
            insrc = asset.engine.resources.first().uri ?: ""
        }
        val vis = true

        outputSd = ServiceDescription(id, name, description, pages, getTags(), logo, agencyAcr, insrc, space, vis)
    }

    override fun getOutput(): Any {
        when (outputSd == null) {
            true -> {
                execute()
                return outputSd!!
            }
            false -> return outputSd!!
        }
    }

    fun getSDPages(): List<String> {
        var content = inputData
        val thePages = mutableListOf<String>()
        var currentPage = ""
        for (line in content.lines()) {
            if (line.startsWith("# ") && currentPage.replace("\n", "") != "") {
                thePages.add(currentPage)
                currentPage = ""
            }
            currentPage += line + "\n"
        }

        thePages.add(currentPage)

        return thePages
    }

    fun getTags(): MutableList<String> {
        var tags = mutableListOf<String>()
        /*"tags":[
                "Security:Open",
                "Technology:Rest/JSON",
                "OpenAPISpec:Swagger",
                "AgencyAcr:ATO",
                "Status:Published",
                "Category:Metadata",
                "Definitions"
                ]

        "features": {
			"registration_required": true,
			"technology": "REST/JSON",
			"space": "apigovau",
			"status": "published"
		},
		"tags": [
			"metadata",
			"definitions"
		],*/
        if (manifest.metadata.features.security != null) tags.add("Security:${manifest.metadata.features.security!!.capitalize()}")
        if (manifest.metadata.features.technology != null) tags.add("Technology:${manifest.metadata.features.technology!!.capitalize()}")
        if (manifest.metadata.features.status != null) tags.add("Status:${manifest.metadata.features.status!!.capitalize()}")
        if (manifest.metadata.features.agencyAcr != null) tags.add("AgencyAcr:${manifest.metadata.features.agencyAcr!!.capitalize()}")
        manifest.metadata.tags.forEach { tags.add(it.capitalize()) }
        return tags
    }
}

@EngineImpl("markdown",
        "markdown",
        "Merges 2 markdown documents based on headings")
class MergeMarkdownEngine() : Engine() {
    enum class MergeType {
        add, insertAfter
    }

    class MergeAction {
        var from = ""
        var action: MergeType = MergeType.add
        var to = ""
    }

    data class MergeActions(val actionList: List<MergeAction>? = null)

    var mergeActions: List<MergeAction> = listOf()

    var mainMarkdown = ""
    var secondMarkdown = ""
    override fun execute() {
        var tempOutput = mainMarkdown
        mergeActions.forEach {
            var from = it.from
            var to = it.to
            var level = getLevel(from)
            var pages = splitPages(tempOutput, level)
            var idxOfFrom = pages.indexOf(pages.filter { it.contains(from) }.first())
            var toPages = splitPages(secondMarkdown, getLevel(to))
            var toContent = toPages.filter { it.contains(to) }.first()
            when (it.action) {
                MergeType.add -> {
                    pages[idxOfFrom] = pages[idxOfFrom] + "\n${toContent.substring(to.length + 1)}"
                }
                MergeType.insertAfter -> {
                    pages.add(idxOfFrom + 1, toContent)
                }
            }
            tempOutput = ""
            pages.forEach { tempOutput += "$it\n" }
        }
        output = tempOutput

    }

    override fun setData(vararg input: Any) {
        try {
            inputData = (input.filter { config!!["map"].equals((it as Pair<String, Any>).first) }
                    .first() as Pair<String, Any>).second.toString()
        } catch (e: Exception) {
            inputData = config!!["map"]!!
        }

        inputData = getMappingString(inputData)
        mergeActions = parseMappingAction(inputData)
        var lastTwo = input.filter { inputIds!!.contains((it as Pair<String, Any>).first) }
        mainMarkdown = (lastTwo.first() as Pair<String, Any>).second as String
        secondMarkdown = (lastTwo.last() as Pair<String, Any>).second as String
    }

    private fun getMappingString(input: String): String {
        if (input.startsWith('{')) {
            return input
        } else {
            var contentStart = input.indexOf("# MergeMapping") + 14
            var contentEnd = input.indexOf("---", input.indexOf("---") + 3)
            var releventString = input.substring(contentStart, contentEnd).trim()
            return releventString
        }
    }

    private fun parseMappingAction(input: String): List<MergeAction> {
        val actions = ObjectMapper().readValue(input, MergeActions::class.java)
        return actions.actionList!!
    }

    private fun getLevel(input: String): String {
        return input.split(' ').first().trim()
    }

    private fun splitPages(input: String, level: String): MutableList<String> {
        val thePages = mutableListOf<String>()
        var currentPage = ""
        for (line in input.lines()) {
            if (line.startsWith("$level ") && currentPage.replace("\n", "") != "") {
                thePages.add(currentPage)
                currentPage = ""
            }
            currentPage += line + "\n"
        }
        thePages.add(currentPage)
        return thePages
    }
}


@EngineImpl("swagger",
        "markdown",
        "Converts swagger documents to markdown")
class SwaggerToMarkdownEngine() : Engine() {
    var docConfig: String? = null

    override fun setData(vararg input: Any) {
        inputData = (input.filter { inputIds!!.contains((it as Pair<String, Any>).first) }
                .first() as Pair<String, Any>).second.toString()

        if (config!!.containsKey("docConfig")) {
            docConfig = (input.filter { config!!["docConfig"].equals((it as Pair<String, Any>).first) }
                    .first() as Pair<String, Any>).second.toString()
        }

    }

    override fun execute() {
        var isYaml = !inputData.trim().startsWith('{')
        var document = ObjectDocument(inputData, isYaml)
        var swaggerVersion = 2
        try {
            swaggerVersion = (document.getValue(".openapi") as String).split('.').first().toInt()
        } catch (e: Exception) {
        }
        var configFile = if (docConfig == null) String(FileCopyUtils.copyToByteArray(ClassPathResource("SwaggerMdConfig.$swaggerVersion.json").inputStream), StandardCharsets.UTF_8) else docConfig!!


        var config = ObjectDocument(configFile, false)
        output = SwaggerToMarkdownConverter(document, config).convert()
    }

    private fun getPagesFromSwagger(swaggerJson: String): String {
        var title = swaggerJson.substring(0, swaggerJson.indexOf("\n")).substring(2)
        var input = swaggerJson.replace(Regex("\\<.*?>"), "")
        var pages = splitPages(input.substring(swaggerJson.indexOf(title) + title.length)).toMutableList()
        var output = ""
        for (i in 0 until pages.count()) {
            pages[i] = pages[i].substring(pages[i].indexOf("## ")).replace("###", "##")
            pages[i] = pages[i].substring(1)
            output += pages[i]
        }
        return output
    }

    private fun splitPages(input: String): List<String> {
        val thePages = mutableListOf<String>()
        var currentPage = ""
        for (line in input.lines()) {
            if (isH2(line) && currentPage.replace("\n", "") != "") {
                thePages.add(currentPage)
                currentPage = ""
            }
            currentPage += line + "\n"
        }

        thePages.add(currentPage)

        return thePages
    }

    private fun isH2(line: String) = line.startsWith("## ")
}

@EngineImpl("docx",
        "markdown",
        "Converts docx documents to markdown")
class DocxToMarkdownEngine() : Engine() {

    override fun execute() {
        val ConvertURI = Config.get("DocConverter")
        val url = "${ConvertURI}pandoc?format=docx&toFormat=gfm&tryExtractImages=true"
        val resp = khttp.post(url, data = ByteArrayInputStream(Base64.getDecoder().decode(inputData)), headers = mapOf("Content-Type" to "application/octet-stream"))
        output = resp.text
    }

    override fun setData(vararg input: Any) {
        inputData = (input.filter { (it as Pair<String, Any>).first.toLowerCase() == "docx" }.last() as Pair<String, Any>).second as String
    }
}

@EngineImpl("wsdl",
        "markdown",
        "Converts wsdl specifications to markdown")
class WSDLEngine : Engine() {
    var outputSd: ServiceDescription? = null
    val reader = WSDLFactory.newInstance().newWSDLReader()


    private fun getWSDL(): Description {

        val dbFactory = DocumentBuilderFactory.newInstance()
        val dBuilder = dbFactory.newDocumentBuilder()
        val xmlInput = InputSource(StringReader(inputData))
        val doc = dBuilder.parse(xmlInput)
        val desc = reader.read(doc)

        return desc
    }


    override fun execute() {


        val id = manifest.metadata.id ?: ""
        val name = manifest.metadata.name ?: ""
        val description = manifest.metadata.description ?: ""
        val pages = getSDPages()
        val logo = manifest.metadata.logo ?: ""
        val space = manifest.metadata.features.space ?: ""
        var insrc = ""
        if (manifest.assets.size > manifest.assetIdx) {
            val asset = manifest.assets[manifest.assetIdx]
            insrc = asset.engine.resources.first().uri ?: ""
        }
        val vis = true

        outputSd = ServiceDescription(id, name, description, pages, getTags(), logo, "", insrc, space, vis)
    }

    override fun getOutput(): Any {
        when (outputSd == null) {
            true -> {
                execute()
                return outputSd!!
            }
            false -> return outputSd!!
        }
    }

    fun getSDPages(): List<String> {
        var content = inputData
        val thePages = mutableListOf<String>()

        val desc = getWSDL()

        for (service in desc.getServices()) {
            var svcString = "# ${service.getQName().getLocalPart()}\n\n"
            for (endpoint in service.getEndpoints()) {
                svcString += "## ${endpoint.getName()}\n\n"
                for (operation in endpoint.getBinding().getBindingOperations()) {

                    svcString += "### ${operation.getQName().getLocalPart()}\n\n"
                    svcString += "### ${operation.getSoapAction()}\n\n"
                    svcString += "### ${operation.getInput().getName()}\n\n"
                    svcString += "### ${operation.getOutput().getName()}\n\n"

                }
            }
            thePages.add(svcString)
        }

        return thePages
    }

    fun getTags(): MutableList<String> {
        var tags = mutableListOf<String>()
        if (manifest.metadata.features.security != null) tags.add("Security:${manifest.metadata.features.security!!.capitalize()}")
        if (manifest.metadata.features.technology != null) tags.add("Technology:${manifest.metadata.features.technology!!.capitalize()}")
        if (manifest.metadata.features.status != null) tags.add("Status:${manifest.metadata.features.status!!.capitalize()}")
        manifest.metadata.tags.forEach { tags.add(it.capitalize()) }
        return tags
    }
}

@EngineImpl("markdown",
        "markdown",
        "Extracts headings from a markdown file")
class ExtractMarkdownHeadersEngine() : Engine() {
    var level: Int = 1
    override fun execute() {
        level = if (config != null && config!!.containsKey("level")) config!!["level"]!!.toInt() else 1
        val builder = StringBuilder()
        getSDPages().forEach { builder.append("$it;") }
        output = builder.toString()
    }

    fun getSDPages(): List<String> {
        var content = inputData
        val thePages = search(level, content.lines())
        return thePages
    }

    val regex = "^([#]+).*".toRegex()

    fun search(level: Int, content: List<String>): List<String> {
        return content.map { it to determineHeader(it) }
                .filter { it.second in 0..level }
                .map { it.first } // get the content
    }

    private fun determineHeader(line: String): Int {
        val result = regex.matchEntire(line) ?: return -1
        return result.groupValues[1].length
    }
}


@EngineImpl("markdown",
        "markdown",
        "Modifies the input based on configuration")
class ModifyTextEngine() : Engine() {
    override fun execute() {
        //TODO

    }
}