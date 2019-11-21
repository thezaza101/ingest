package au.gov.api.ingest

import au.gov.api.config.Config
import au.gov.api.ingest.preview.EngineImpl
import io.github.swagger2markup.Swagger2MarkupConverter
import io.github.swagger2markup.builder.Swagger2MarkupConfigBuilder
import org.apache.commons.configuration2.builder.fluent.Configurations
import java.io.ByteArrayInputStream
import java.util.*

abstract class Engine : PipeObject() {
    var inputIds:List<String>? = null
    var inputData = ""
    var outputId = ""
    var config:HashMap<String,String>? = null
    open var output: String? = null

    override val type: PipeType = PipeType.Engine

    fun setInputNames(names:List<String>) {
        inputIds = names
    }

    fun setOutputName(name:String) {
        outputId = name
    }

    fun setConfigration(conf:HashMap<String,String>?) {
        config = conf
    }

    fun setAllConfig(inNames:List<String>, outName:String, engConf:HashMap<String,String>?) {
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
        inputData = (input.filter { config!!["map"].equals((it as Pair<String, Any>).first) }
                .first() as Pair<String, Any>).second.toString()
        inputData = getMappingString(inputData)
        mergeActions = parseMappingAction(inputData)
        var lastTwo = input.filter { inputIds!!.contains((it as Pair<String, Any>).first) }
        mainMarkdown = (lastTwo.first() as Pair<String, Any>).second as String
        secondMarkdown = (lastTwo.last() as Pair<String, Any>).second as String
    }

    private fun getMappingString(input: String): String {
        var contentStart = input.indexOf("# MergeMapping")
        var contentEnd = input.indexOf("---", input.indexOf("---") + 3)
        var releventString = input.substring(contentStart, contentEnd)
        return releventString
    }

    private fun parseMappingAction(input: String): List<MergeAction> {
        var maps: MutableList<String> = mutableListOf()
        var tempStr = ""
        var tempActionStr = ""
        var inQuotes = false
        input.forEach {
            if (it.equals('\"')) {
                inQuotes = !inQuotes
                if (tempStr.trim().isNotBlank()) {
                    maps.add(tempStr.replace("\"", "").trim())
                }
                if (tempActionStr.trim().isNotBlank()) {
                    maps.add(tempActionStr.replace("\"", "").trim())
                    tempActionStr = ""
                }
                tempStr = ""
            }
            if (inQuotes) {
                tempStr += it
            } else {
                if (maps.count() > 0) {
                    tempActionStr += it
                }
            }
        }
        var tempMaps: MutableList<String> = mutableListOf()
        var output: MutableList<MergeAction> = mutableListOf()

        maps.forEach {
            if (it.count() > 1) {
                tempMaps.add(it)
            }
        }
        maps = tempMaps

        var i = 0
        while (i < maps.count()) {
            var tempAction: MergeAction = MergeAction()
            tempAction.from = maps[i]
            tempAction.action = MergeType.valueOf(maps[i + 1])
            tempAction.to = maps[i + 2]
            output.add(tempAction)
            i += 3
        }
        return output.toList()
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
    override fun execute() {
        var configs = Configurations().properties("config.properties")
        var swagger2MarkupConfig = Swagger2MarkupConfigBuilder(configs).build()
        var converterBuilder = Swagger2MarkupConverter.from(inputData)
        converterBuilder.withConfig(swagger2MarkupConfig)
        var converter = converterBuilder.build()
        output = getPagesFromSwagger(converter.toString())
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