package au.gov.api.ingest.converters.formats.markdown

import au.gov.api.ingest.converters.models.DataSource

class MarkdownSection(basePath: String, innerSections: List<MarkdownGenerator>, ds: DataSource) :
        MarkdownGenerator(ds) {
    var path = basePath
    var innerMDG: MutableList<MarkdownGenerator> = innerSections.toMutableList()
    override fun generateString(): String {
        var sb: StringBuilder = StringBuilder()
        var prod = true
        if (prod) {
            try {
                innerMDG.forEach { sb.append(it.generateString()) }
            } catch (e: Exception) {
                println("Possible missing section \n ${e.toString()}")
                return ""
            }
        } else {
            innerMDG.forEach { sb.append(it.generateString()) }
        }
        return sb.toString()
    }

    fun generateChildSections(path: String): MutableList<MarkdownSection> {
        var output: MutableList<MarkdownSection> = mutableListOf()
        var paths = ds.getQueryValuePaths(path)
        paths.forEach { output.add(MarkdownSection(it, mutableListOf(), ds)) }
        return output
    }
}