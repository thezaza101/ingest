package au.gov.api.ingest

import com.fasterxml.jackson.databind.ObjectMapper
import java.util.ArrayList

//Manifest
class Manifest {
    var metadata: Metadata = Metadata()
    internal var assetIdx = 0
    var assets = ArrayList<Assets>()

    companion object{
        @JvmStatic
        fun readMnifest(manifestString:String) : Manifest {
            return ObjectMapper().readValue(manifestString, Manifest::class.java)
        }
    }
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
    var security: String? = null
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