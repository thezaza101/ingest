package au.gov.api.ingest

import java.net.URL

class PipelineBuilder {

    var Pipes:MutableList<Pipeline> = mutableListOf()
    var ManifestRef:Manifest = Manifest()

    constructor(manifestString: String){
        ManifestRef = Manifest.readMnifest(manifestString)
    }
    constructor(manifest: Manifest){
        ManifestRef = manifest
    }

    fun buildPipeline() {
        var idxOfAsset = 0
        for (asset in ManifestRef.assets) {
            var pl = Pipeline(ManifestRef,idxOfAsset)
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
            idxOfAsset++
        }
    }


    fun getClassFromString(className:String): Class<*>? {
        return Class.forName(className)
    }

    fun executePipes():MutableList<Any> {
        var outputs:MutableList<Any> = mutableListOf()
        Pipes.forEach { it.execute()
            outputs.add(it.getLastPipelineOnject())}

        return MapPipeOutputs(outputs)
    }
    private fun MapPipeOutputs(outputs:MutableList<Any>) : MutableList<Any> {
        var newOutputs:MutableList<Any> = mutableListOf()
        for (i in 0 until ManifestRef.assets.count()) {
            when(ManifestRef.assets[i].type) {
                "api_description" -> {newOutputs.add(outputs[i] as String)}
                else -> newOutputs.add(outputs[i])
            }
        }
        return newOutputs
    }

    companion object{
        @JvmStatic
        fun getTextOfFlie(uri:String):String = URL(uri).readText()
    }
}