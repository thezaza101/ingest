package au.gov.api.ingest

import java.net.URL

abstract class Data(source:String) : PipeObject() {
    open var dataSource = source
    open var output:String? = null

    override val type: PipeType = PipeType.Data

    open fun getString():String {
        when (output==null) {
            true ->{execute()
                return output!!}
            false -> return output!!
        }
    }
}

class PolledData(source:String) : Data(source) {
    override fun execute() {
        output = URL(dataSource).readText()
    }
}