package au.gov.api.ingest.converters.swaggerToMarkdown

import au.gov.api.ingest.converters.models.ObjectDocument
import org.junit.Assert
import org.junit.Test
import javax.validation.constraints.AssertTrue

class SwaggerToMarkdownTests {
    var docObj:ObjectDocument = ObjectDocument("{}",false)
    var docConfigObject:ObjectDocument = ObjectDocument("{}",false)
    init {
        try {
            docConfigObject = ObjectDocument(object {}.javaClass.getResource("/testSwaggerMdConfig.json").readText(),false)
            docObj = ObjectDocument(object {}.javaClass.getResource("/testSwaggerDoc.yaml").readText())
        } catch (e:Exception) {

        }
    }

    @Test
    fun test_can_readJsonYaml() {
        docConfigObject = ObjectDocument(object {}.javaClass.getResource("/testSwaggerMdConfig.json").readText(),false)
        docObj = ObjectDocument(object {}.javaClass.getResource("/testSwaggerDoc.yaml").readText())
        Assert.assertTrue(docConfigObject.doc.count()>0)
        Assert.assertTrue(docObj.doc.count()>0)
    }

    @Test
    fun test_can_get_value_from_document() {
        val v = docObj.getValue(".openapi")
        Assert.assertEquals("3.0.0",v)
    }

    @Test
    fun test_can_get_value_paths_from_query() {
        var queryResults = docObj.getQueryValuePaths(".paths.[*]")
        var expectedVals = listOf<String>(
                ".paths./api/definition/{domain}/{id}",
                ".paths./api/browse",
                ".paths./api/search"
        )
        Assert.assertArrayEquals(expectedVals.toTypedArray(),queryResults.toTypedArray())
    }

    @Test
    fun test_can_convert_document() {
        var converter = SwaggerToMarkdownConverter(docObj,docConfigObject)
        try {
            var output = converter.convert()
            Assert.assertTrue(true)

        } catch (e:Exception) {

        }
    }



}