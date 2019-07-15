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

        outputSd = ServiceDescription(id,name,description,pages, mutableListOf(),logo,"",insrc,space,vis)
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
}