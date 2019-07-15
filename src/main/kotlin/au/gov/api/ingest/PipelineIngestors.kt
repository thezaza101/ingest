package au.gov.api.ingest

abstract class Ingestor : PipeObject() {
    override val type: PipeType = PipeType.Ingestor
    abstract fun setData(input:Any)
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

data class ServiceDescription(val id: String = "",val name: String = "", val description: String = "", val pages: List<String> = listOf(""),
                              var tags: MutableList<String> = mutableListOf(), var logo: String = "", var space: String = "")