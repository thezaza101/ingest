package au.gov.api.ingest

import org.junit.Assert
import org.junit.Test

import au.gov.api.ingest.SingleMarkdownToServiceDesignEngine
import au.gov.api.ingest.ServiceDescription
import au.gov.api.ingest.Assets

class SingleMarkdownEngineTests{


    val content = """# Page1

## A heading on page 1

some content about page 1

# Page2

## A heading on page 2

some content about page 2


"""
    val example = content 

    val mdfm = SingleMarkdownToServiceDesignEngine()
    val sd:ServiceDescription

    init{
        mdfm.inputIds = listOf("md1")
        mdfm.setData(Pair<String,String>("md1",example))
        sd = mdfm.getOutput() as ServiceDescription
    }


    @Test
    fun test_there_are_two_pages(){
        Assert.assertEquals(2, sd.pages.size)
        Assert.assertTrue(sd.pages[0].startsWith("# Page1")) 
        Assert.assertTrue(sd.pages[1].startsWith("# Page2")) 
    }



}

