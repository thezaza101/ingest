package au.gov.api.ingest

abstract class Engine : PipeObject() {
    var inputData = ""
    open var output:String? = null

    override val type: PipeType = PipeType.Engine

    open fun setData(vararg input: Any) {
        inputData = input.last() as String
    }
    open fun getOutput(): Any {
        when (output==null) {
            true ->{execute()
                return output!!}
            false -> return output!!
        }
    }


}

class StripFrontMatterEngine : Engine() {
    override fun execute() {
        var contentStart = inputData.indexOf("---",inputData.indexOf("---")+3)
        output = inputData.substring(contentStart+3).trim()
    }
}

class SingleMarkdownToServiceDesignEngine : Engine() {
    var outputSd:ServiceDescription? = null

    override fun execute() {
        val id = manifest.metadata.id!!
        val name = manifest.metadata.name!!
        val description = manifest.metadata.description!!
        val pages = getSDPages()
        val logo = manifest.metadata.logo!!
        val space = manifest.metadata.features.space!!
        val insrc = manifest.assets[manifest.assetIdx].engine.resources.first().uri!!
        val vis =  true








        outputSd = ServiceDescription(id,name,description,pages, getTags(),logo,"",insrc,space,vis)
    }
    override fun getOutput(): Any {
        when (outputSd==null) {
            true ->{execute()
                return outputSd!!}
            false -> return outputSd!!
        }
    }

    fun getSDPages() : List<String> {
        var content = inputData
        val thePages = mutableListOf<String>()
        var currentPage = ""
        for(line in content.lines()){
            if(line.startsWith("# ") && currentPage.replace("\n","") != ""){
                thePages.add(currentPage)
                currentPage = ""
            }
            currentPage += line + "\n"
        }

        thePages.add(currentPage)

        return thePages
    }

    fun getTags() : MutableList<String> {
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
        tags.add("Security:${manifest.metadata.features.security!!.capitalize()}")
        tags.add("Technology:${manifest.metadata.features.technology!!.capitalize()}")
        tags.add("Status:${manifest.metadata.features.status!!.capitalize()}")
        manifest.metadata.tags.forEach { tags.add(it.capitalize()) }
        return tags
    }
}