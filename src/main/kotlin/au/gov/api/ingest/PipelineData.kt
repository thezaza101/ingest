package au.gov.api.ingest

import au.gov.api.config.Config
import au.gov.api.ingest.preview.DataImpl
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import java.net.URL
import java.util.*

abstract class Data(source:String,r:String) : PipeObject() {
    open var dataSource = source
    open var id = r
    open var output: String? = null

    override val type: PipeType = PipeType.Data

    open fun getString(): String {
        when (output == null) {
            true -> {
                execute()
                return output!!
            }
            false -> return output!!
        }
    }
}

@DataImpl("URL", "Can get data from any publicly available URL")
class PolledData(source:String, role:String) : Data(source,role) {
    override fun execute() {
        output = URL(dataSource).readText()
    }
}

@DataImpl("Any", "Can get data from any uploaded files")
class UploadedData(source:String, role:String) : Data(source,role) {
    override fun execute() {
        val file =  repository!!.findFileById(dataSource)
        output = file.content
    }
}

@DataImpl("Local", "Can get data from any local file")
class PolledSetData(source:String, role:String) : Data(source,role) {
    override fun execute() {
        output = object {}.javaClass.getResource(dataSource).readText()
    }
}