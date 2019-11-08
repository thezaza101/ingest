package au.gov.api.ingest

import org.junit.Assert
import org.junit.Test
import java.lang.Exception

class EngineTests {
    val example = object {}.javaClass.getResource("/ManifestExample1.json").readText()
    val manifest = PipelineBuilder(example)
    init {
        manifest.buildPipeline()
    }

    private fun executeData() {
        manifest.Pipes.first().finaliseData()
    }
    private fun executeEngine() {
        manifest.Pipes.first().finaliseEngine()
    }

    @Test
    fun test_has_correct_number_of_pipe_objects1() {
        Assert.assertEquals(8,manifest.Pipes.first().pipeline.count())
    }

    @Test
    fun test_can_poll_data() {
        executeData()
        Assert.assertEquals(3,manifest.Pipes.first().ingestObjs.count())
    }

    @Test
    fun test_can_strip_frontmatter() {
        executeData()
        //StripFrontMatter
        (manifest.Pipes.first().pipeline[3] as Engine).setData(*manifest.Pipes.first().ingestObjs.toTypedArray())
        manifest.Pipes.first().pipeline[3].execute()
        var strippedData = ((manifest.Pipes.first().pipeline[3] as Engine).getOutput() as String).trim()
        Assert.assertTrue(!strippedData.startsWith("---"))
    }

    @Test
    fun test_can_convert_swagger_to_markdown() {
        executeData()
        //StripFrontMatter
        (manifest.Pipes.first().pipeline[3] as Engine).setData(*manifest.Pipes.first().ingestObjs.toTypedArray())
        manifest.Pipes.first().pipeline[3].execute()
        var strippedData = ((manifest.Pipes.first().pipeline[3] as Engine).getOutput() as String).trim()
        manifest.Pipes.first().ingestObjs.add(Pair((manifest.Pipes.first().pipeline[3] as Engine).toString(), strippedData))
        //SwaggerToMarkdown
        (manifest.Pipes.first().pipeline[4] as Engine).setData(*manifest.Pipes.first().ingestObjs.toTypedArray())
        manifest.Pipes.first().pipeline[4].execute()
        var swaggermd = ((manifest.Pipes.first().pipeline[4] as Engine).getOutput() as String).trim()
        manifest.Pipes.first().ingestObjs.add(Pair((manifest.Pipes.first().pipeline[4] as Engine).toString(), swaggermd))

        Assert.assertTrue(swaggermd.startsWith("# Overview"))
    }

    @Test
    fun test_can_merge_swagger_and_markdown() {
        executeData()
        //StripFrontMatter
        (manifest.Pipes.first().pipeline[3] as Engine).setData(*manifest.Pipes.first().ingestObjs.toTypedArray())
        manifest.Pipes.first().pipeline[3].execute()
        var strippedData = ((manifest.Pipes.first().pipeline[3] as Engine).getOutput() as String).trim()
        manifest.Pipes.first().ingestObjs.add(Pair((manifest.Pipes.first().pipeline[3] as Engine).toString(), strippedData))
        //SwaggerToMarkdown
        (manifest.Pipes.first().pipeline[4] as Engine).setData(*manifest.Pipes.first().ingestObjs.toTypedArray())
        manifest.Pipes.first().pipeline[4].execute()
        var swaggermd = ((manifest.Pipes.first().pipeline[4] as Engine).getOutput() as String).trim()
        manifest.Pipes.first().ingestObjs.add(Pair((manifest.Pipes.first().pipeline[4] as Engine).toString(), swaggermd))
        //MergeMarkdown
        (manifest.Pipes.first().pipeline[5] as Engine).setData(*manifest.Pipes.first().ingestObjs.toTypedArray())
        manifest.Pipes.first().pipeline[5].execute()
        var mergedmd = ((manifest.Pipes.first().pipeline[5] as Engine).getOutput() as String).trim()
        manifest.Pipes.first().ingestObjs.add(Pair((manifest.Pipes.first().pipeline[5] as Engine).toString(), swaggermd))
        Assert.assertTrue(mergedmd.contains("*Version* : 1.0.0"))
        Assert.assertTrue(mergedmd.contains("# Getting Started"))
    }

    @Test
    fun test_can_create_sd_from_md() {
        executeData()
        executeEngine()
        Assert.assertTrue(manifest.Pipes.first().ingestObjs.last().second is ServiceDescription)
        var sd = manifest.Pipes.first().ingestObjs.last().second as ServiceDescription
        Assert.assertEquals(manifest.ManifestRef.metadata.name,sd.name)
    }




}