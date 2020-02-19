package au.gov.api.ingest.converters.formats.markdown

import au.gov.api.ingest.converters.models.DataSource

class MarkdownListGenerator(str: String, ds: DataSource, type: ListType = ListType.BULLET) : MarkdownGenerator(ds) {
    enum class ListType {
        BULLET, ORDERED
    }

    var listType = type
    var path = str

    override fun generateString(): String {
        var sb: StringBuilder = StringBuilder()
        var list: List<String> = ds.getValue(path) as List<String>
        if (listType == ListType.BULLET) {
            list.forEach { sb.append("* $it ${System.lineSeparator()}") }
        } else if (listType == ListType.ORDERED) {
            var idx = 1
            list.forEach { sb.append("$idx. $it ${System.lineSeparator()}") }
        }
        return sb.toString()
    }
}