package au.gov.api.ingest

import java.net.URL

class Pipeline {

    var ingestObjs:MutableList<Any> = mutableListOf()
    var manifest:Manifest? = Manifest()
    constructor(meta:Manifest, assetIdx:Int) {
        manifest = meta
        manifest!!.assetIdx = assetIdx}

    var pipeline:MutableList<PipeObject> = mutableListOf()

    fun addToPipeline(input:PipeObject) {
        var element = input
        element.manifest = manifest!!
        pipeline.add(element)
    }
    fun finaliseData() {
        pipeline.filter { it.type == PipeObject.PipeType.Data }
                .forEach { it.execute()
                    ingestObjs.add((it as Data).getString())}
    }
    fun finaliseEngine() {
        pipeline.filter { it.type == PipeObject.PipeType.Engine }
                .forEach { (it as Engine).setData(*ingestObjs.toTypedArray())
                    ingestObjs.add(it.getOutput() )}
    }
    fun finaliseIngest() {
        pipeline.filter { it.type == PipeObject.PipeType.Ingestor }
                .forEach { (it as Ingestor).setData(ingestObjs.last())
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

abstract class PipeObject {
    enum class PipeType {
        Data,Engine,Ingestor
    }
    abstract fun execute()
    abstract val type:PipeType
    var manifest:Manifest = Manifest()
}



