package au.gov.api.ingest.converters.swaggerToMarkdown

import au.gov.api.ingest.converters.formats.markdown.MDTextFormats
import au.gov.api.ingest.converters.formats.markdown.MarkdownSection
import au.gov.api.ingest.converters.formats.markdown.MarkdownTextGenerator
import au.gov.api.ingest.converters.models.ObjectDocument
import org.junit.Assert
import org.junit.Test

class SwaggerToMarkdownTests {
    var docObj: ObjectDocument = ObjectDocument("{}", false)
    var docConfigObject: ObjectDocument = ObjectDocument("{}", false)

    fun initDefaultTestDoc() {
        try {
            docConfigObject = ObjectDocument(object {}.javaClass.getResource("/testSwaggerMdConfig.json").readText(), false)
            docObj = ObjectDocument(object {}.javaClass.getResource("/testSwaggerDoc.yaml").readText())
        } catch (e: Exception) {

        }
    }

    @Test
    fun test_can_readJsonYaml() {
        docConfigObject = ObjectDocument(object {}.javaClass.getResource("/testSwaggerMdConfig.json").readText(), false)
        docObj = ObjectDocument(object {}.javaClass.getResource("/testSwaggerDoc.yaml").readText())
        Assert.assertTrue(docConfigObject.doc.count() > 0)
        Assert.assertTrue(docObj.doc.count() > 0)
    }

    @Test
    fun test_can_get_value_from_document() {
        initDefaultTestDoc()
        val v = docObj.getValue(".openapi")
        Assert.assertEquals("3.0.0", v)
    }

    @Test
    fun test_can_get_value_paths_from_query() {
        initDefaultTestDoc()
        var queryResults = docObj.getQueryValuePaths(".paths.[*]")
        var expectedVals = listOf<String>(
                ".paths./api/definition/{domain}/{id}",
                ".paths./api/browse",
                ".paths./api/search"
        )
        Assert.assertArrayEquals(expectedVals.toTypedArray(), queryResults.toTypedArray())
    }

    @Test
    fun test_can_convert_document() {
        initDefaultTestDoc()
        var converter = SwaggerToMarkdownConverter(docObj, docConfigObject)
        try {
            converter.convert()
            Assert.assertTrue(true)
        } catch (e: Exception) {
            Assert.assertTrue(false)
        }
    }

    @Test
    fun test_markdown_text_formats() {
        val text = "Hello World!"
        docObj = ObjectDocument("{\"some_field\":\"$text\"}", false)


        var gen: MarkdownTextGenerator = MarkdownTextGenerator(".some_field", MDTextFormats.NONE, docObj, false)
        var output = gen.generateString()
        Assert.assertEquals(text, output)

        gen = MarkdownTextGenerator(".some_field", MDTextFormats.BOLD, docObj, false)
        output = gen.generateString()
        Assert.assertTrue((output.startsWith("__") && output.endsWith("__")))

        gen = MarkdownTextGenerator(".some_field", MDTextFormats.ITALIC, docObj, false)
        output = gen.generateString()
        Assert.assertTrue((output.startsWith("_") && output[1] != '_' && output[output.count() - 2] != '_' && output.endsWith("_")))

        gen = MarkdownTextGenerator(".some_field", MDTextFormats.STRIKE, docObj, false)
        output = gen.generateString()
        Assert.assertTrue((output.startsWith("~~") && output.endsWith("~~")))

        gen = MarkdownTextGenerator(".some_field", MDTextFormats.QUOTE, docObj, false)
        output = gen.generateString()
        Assert.assertTrue((output.startsWith("> ")))

        gen = MarkdownTextGenerator(".some_field", MDTextFormats.CODE, docObj, false)
        output = gen.generateString()
        Assert.assertTrue((output.startsWith("```") && output.endsWith("```")))

        gen = MarkdownTextGenerator(".some_field", MDTextFormats.H1, docObj, false)
        output = gen.generateString()
        Assert.assertTrue((output.startsWith("#") && output[1] != '#'))

        gen = MarkdownTextGenerator(".some_field", MDTextFormats.H2, docObj, false)
        output = gen.generateString()
        Assert.assertTrue((output.startsWith("##") && output[2] != '#'))

        gen = MarkdownTextGenerator(".some_field", MDTextFormats.H3, docObj, false)
        output = gen.generateString()
        Assert.assertTrue((output.startsWith("###") && output[3] != '#'))

        gen = MarkdownTextGenerator(".some_field", MDTextFormats.H4, docObj, false)
        output = gen.generateString()
        Assert.assertTrue((output.startsWith("####") && output[4] != '#'))

        gen = MarkdownTextGenerator(".some_field", MDTextFormats.H5, docObj, false)
        output = gen.generateString()
        Assert.assertTrue((output.startsWith("#####") && output[5] != '#'))

        gen = MarkdownTextGenerator(".some_field", MDTextFormats.H6, docObj, false)
        output = gen.generateString()
        Assert.assertTrue((output.startsWith("######") && output[6] != '#'))

        gen = MarkdownTextGenerator(".some_field", MDTextFormats.USERDEFINED, docObj, false, "somecustomformatter %")
        output = gen.generateString()
        Assert.assertTrue((output.startsWith("somecustomformatter")))
    }


    @Test
    fun test_markdown_can_ignore_missing_section() {
        val text = "Hello World!"
        docObj = ObjectDocument("{\"some_field\":\"$text\"}", false)

        //Check that it works first
        var gen = MarkdownSection("", listOf(
                MarkdownTextGenerator("Hello", MDTextFormats.H1, docObj),
                MarkdownTextGenerator(".some_field", MDTextFormats.BOLD, docObj, false)
        ), docObj)

        var expectedString = "# Hello${System.getProperty("line.separator")}__${text}__"
        var actualString = gen.generateString()
        Assert.assertEquals(expectedString, actualString)

        //Change it to a path that doesnt exist
        gen = MarkdownSection("", listOf(
                MarkdownTextGenerator("Hello", MDTextFormats.H1, docObj),
                MarkdownTextGenerator(".some_field1", MDTextFormats.BOLD, docObj, false)
        ), docObj)
        actualString = gen.generateString()

        expectedString = ""
        Assert.assertEquals(expectedString, actualString)
    }


}