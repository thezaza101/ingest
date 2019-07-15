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
        val id = meta.id!!
        val name = meta.name!!
        val description = meta.description!!
        val pages = getSDPages()
        val logo = meta.logo!!
        val space = meta.features.space!!

        outputSd = ServiceDescription(id,name,description,pages, mutableListOf(),logo,space)
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