package au.gov.api.ingest

import java.net.URL

class Pipeline {

    var data:MutableList<String> = mutableListOf()
    var ingestObjs:MutableList<Any> = mutableListOf()
    var metadata:Metadata? = Metadata()
    constructor(meta:Metadata) {metadata = meta}

    var pipeline:MutableList<PipeObject> = mutableListOf()

    fun addToPipeline(input:PipeObject) {
        var element = input
        element.meta = metadata!!
        pipeline.add(element)
    }
    fun finaliseData() {
        pipeline.filter { it.type == PipeObject.PipeType.Data }
                .forEach { it.execute()
                data.add((it as Data).getString())}
    }
    fun finaliseEngine() {
        pipeline.filter { it.type == PipeObject.PipeType.Engine }
                .forEach { (it as Engine).setData(*data.toTypedArray())
                    ingestObjs.add(it.getOutput() )}
    }
    fun finaliseIngest() {
        pipeline.filter { it.type == PipeObject.PipeType.Ingestor }
                .forEach { (it as Ingestor).setData(ingestObjs.first())
                    it.execute() }
    }
    fun finalise() {
        finaliseData()
        finaliseEngine()
    }
    fun ingest() {
        finaliseIngest()
    }
    fun execute() {
        finalise()
        ingest()
    }

}

abstract class PipeObject() {
    enum class PipeType {
        Data,Engine,Ingestor
    }
    abstract fun execute()
    abstract val type:PipeType
    var meta:Metadata = Metadata()
}

abstract class Data(source:String) : PipeObject() {
    override val type: PipeType = PipeType.Data

    abstract fun getString():String
}
abstract class Engine() : PipeObject() {
    override val type: PipeType = PipeType.Engine
    abstract fun setData(vararg input:String)
    abstract fun getOutput():Any
}
abstract class Ingestor() : PipeObject() {
    override val type: PipeType = PipeType.Ingestor
    abstract fun setData(input:Any)
}

class PolledData(source:String) : Data(source) {
    var dataSource = source
    var rawString:String? = null
    override fun execute() {
        rawString = URL(dataSource).readText()
    }
    override fun getString():String {
        when (rawString==null) {
            true ->{execute()
                return rawString!!}
            false -> return rawString!!
        }
    }
}


class SingleMarkdownToServiceDesignEngine : Engine() {
    var inputData = ""
    var output:ServiceDescription? = null

    override fun setData(vararg input: String) {
        inputData = input.first()
    }
    override fun execute() {
        val name = meta.name!!
        val description = meta.description!!
        val pages = getSDPages()
        val logo = meta.logo!!
        val space = "cats"
        output = ServiceDescription(name,description,pages, mutableListOf(),logo,space)
    }

    override fun getOutput(): Any {
        when (output==null) {
            true ->{execute()
                return output!!}
            false -> return output!!
        }
    }


    fun getSDPages() : List<String> {
        var contentStart = inputData.lastIndexOf("---")
        var content = inputData.substring(contentStart+3).trim()

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

class ServiceDescriptionIngestor() : Ingestor() {
    var serviceDescription:ServiceDescription = ServiceDescription()
    override fun setData(input: Any) {
        serviceDescription = input as ServiceDescription
    }
    override fun execute() {
        var x = "cats!"
        var y = x
    }
}

data class ServiceDescription(val name: String = "", val description: String = "", val pages: List<String> = listOf(""),
                              var tags: MutableList<String> = mutableListOf(), var logo: String = "",
                              var space: String = "", var ingestSource: String = "")

