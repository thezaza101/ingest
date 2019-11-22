package au.gov.api.ingest

import org.junit.Assert
import org.junit.Test

class PipelineBuilderTests {
    val example = object {}.javaClass.getResource("/example.json").readText()
    val manifest = PipelineBuilder(example)

    @Test
    fun test_can_build_pipeline() {
        var pipeBuilt: Boolean = false

        try {
            manifest.buildPipeline(PipelineBuilder.AssetMechanism.All, null)
            pipeBuilt = true
        } catch (e: Exception) {

        }
        Assert.assertEquals(true, pipeBuilt)
    }

    @Test
    fun test_has_correct_number_of_pipes() {
        manifest.buildPipeline(PipelineBuilder.AssetMechanism.All, null)
        Assert.assertEquals(1, manifest.Pipes.count())
    }

    @Test
    fun test_has_correct_number_of_pipe_objects() {
        manifest.buildPipeline(PipelineBuilder.AssetMechanism.All, null)
        Assert.assertEquals(4, manifest.Pipes.first().pipeline.count())
    }
}