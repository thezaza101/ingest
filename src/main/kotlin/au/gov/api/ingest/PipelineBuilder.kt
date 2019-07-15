package au.gov.api.ingest

import java.net.URL
import java.util.ArrayList
import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.channels.Pipe
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.jvm.kotlinFunction


class PipelineBuilder {

    var Pipes:MutableList<Pipeline> = mutableListOf()
    var Manifest:Manifest = Manifest()

    constructor(manifestString: String){
        readMnifest(manifestString)
    }

    fun buildPipeline() {
        for (asset in Manifest.assets) {
            var pl = Pipeline(Manifest.metadata)
            for (resource in asset.engine.resources) {
                when (resource.mechanism!!) {
                    "poll" -> pl.addToPipeline(PolledData(resource.uri!!))
                }
            }
            for (eng in asset.engine.names) {
                pl.addToPipeline(getClassFromString("au.gov.api.ingest.${eng}Engine")!!.getConstructor().newInstance() as PipeObject)
            }

            when (asset.type){
                "api_description" -> pl.addToPipeline(ServiceDescriptionIngestor())
            }
            Pipes.add(pl)
        }
    }


    fun getClassFromString(className:String): Class<*>? {
        return Class.forName(className)
    }

    fun executePipes() {
        Pipes.forEach { it.execute() }
    }

    fun readMnifest(manifestString:String) {
        Manifest = ObjectMapper().readValue(manifestString, Manifest::class.java)
    }

    fun buildPipes() {

    }

    companion object{
        @JvmStatic
        fun getTextOfFlie(uri:String):String = URL(uri).readText()
    }

}


//Manifest
class Manifest {
    var metadata: Metadata = Metadata()
    var assets = ArrayList<Assets>()
}

class Metadata {
    var id: String? = null
    var name: String? = null
    var description: String? = null
    var logo: String? = null
    var features: Features = Features()
    var tags = ArrayList<String>()
    var misc = ArrayList<Pair<String,String>>()
    var topics = ArrayList<String>()
}

class Features {
    var registration_required: Boolean = false
    var technology: String? = null
    var status: String? = null
    var space: String? = null

}


class Assets {
    var type: String? = null
    var misc = ArrayList<Pair<String,String>>()
    var engine: EngineDec = EngineDec()
}

class EngineDec {
    var names = ArrayList<String>()
    var resources = ArrayList<Resources>()
}

class Resources {
    var role: String? = null
    var uri: String? = null
    var mechanism: String? = null
}