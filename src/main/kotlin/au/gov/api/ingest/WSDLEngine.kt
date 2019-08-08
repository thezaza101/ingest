package au.gov.api.ingest

import org.ow2.easywsdl.wsdl.WSDLFactory
import org.ow2.easywsdl.wsdl.api.Description

import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource
import java.io.StringReader


class WSDLEngine: Engine() {
    var outputSd:ServiceDescription? = null
    val reader=WSDLFactory.newInstance().newWSDLReader()


	private fun getWSDL():Description{
	
		val dbFactory = DocumentBuilderFactory.newInstance()
		val dBuilder = dbFactory.newDocumentBuilder()
		val xmlInput = InputSource(StringReader(inputData))
		val doc = dBuilder.parse(xmlInput)
        val desc = reader.read(doc)  
	
		return desc
	}


    override fun execute() {


        val id = manifest.metadata.id ?: ""
        val name = manifest.metadata.name ?: ""
        val description = manifest.metadata.description ?: ""
        val pages = getSDPages()
        val logo = manifest.metadata.logo ?: ""
        val space = manifest.metadata.features.space ?: ""
        var insrc = ""
        if(manifest.assets.size > manifest.assetIdx){
            val asset = manifest.assets[manifest.assetIdx]
            insrc = asset.engine.resources.first().uri ?: ""
        }
        val vis =  true

        outputSd = ServiceDescription(id,name,description,pages, getTags(),logo,"",insrc,space,vis)
    }
    override fun getOutput(): Any {
        when (outputSd==null) {
            true ->{execute()
                return outputSd!!}
            false -> return outputSd!!
        }
    }

    fun getSDPages() : List<String> {
        var content = inputData
        val thePages = mutableListOf<String>()

		val desc = getWSDL()

		for(service in desc.getServices()){
			var svcString = "# ${service.getQName().getLocalPart()}\n\n"
			for(endpoint in service.getEndpoints()){
				svcString += "## ${endpoint.getName()}\n\n"
				for(operation in endpoint.getBinding().getBindingOperations()){

					svcString += "### ${operation.getQName().getLocalPart()}\n\n"
					svcString += "### ${operation.getSoapAction()}\n\n"
					svcString += "### ${operation.getInput().getName()}\n\n"
					svcString += "### ${operation.getOutput().getName()}\n\n"

				}
			}
			thePages.add(svcString)
		}

        return thePages
    }

    fun getTags() : MutableList<String> {
        var tags = mutableListOf<String>()
        if(manifest.metadata.features.security != null) tags.add("Security:${manifest.metadata.features.security!!.capitalize()}")
        if(manifest.metadata.features.technology != null)tags.add("Technology:${manifest.metadata.features.technology!!.capitalize()}")
        if(manifest.metadata.features.status != null) tags.add("Status:${manifest.metadata.features.status!!.capitalize()}")
        manifest.metadata.tags.forEach { tags.add(it.capitalize()) }
        return tags
    }
}
