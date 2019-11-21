package au.gov.api.ingest

import org.junit.Assert
import org.junit.Test

import au.gov.api.ingest.WSDLEngine
import au.gov.api.ingest.ServiceDescription

class WSDLEngineTests{


    val example = object {}.javaClass.getResource("/currency-converter.wsdl").readText()

    val engine = WSDLEngine()
    val sd:ServiceDescription

    init{
        engine.inputIds = listOf("wsdl")
        engine.setData(Pair<String,String>("wsdl",example))
        sd = engine.getOutput() as ServiceDescription
    }

    @Test
    fun test_there_is_one_page(){
        Assert.assertEquals(1, sd.pages.size)
        Assert.assertTrue(sd.pages[0].startsWith("# CurrencyConvertor")) 

	}

	@Test
	fun service_page_contains_bindings(){
		println(sd.pages[0])
        Assert.assertTrue(sd.pages[0].contains("## CurrencyConvertorSoap")) 
        Assert.assertTrue(sd.pages[0].contains("## CurrencyConvertorSoap12")) 
        Assert.assertTrue(sd.pages[0].contains("## CurrencyConvertorHttpGet")) 
        Assert.assertTrue(sd.pages[0].contains("## CurrencyConvertorHttpPost")) 
    }



}

