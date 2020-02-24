package au.gov.api.ingest.converters.formats.markdown

import au.gov.api.ingest.converters.models.DataSource
import au.gov.api.ingest.helpers.NestedListEnumerator

class MarkdownTableGenerator(
        hedings: List<String>,
        rowVals: List<String>,
        ds: DataSource,
        colAlignments: List<ColumnAlignment>? = null,
        colKeyAsValue: List<Boolean>? = null

) : MarkdownGenerator(ds) {
    enum class ColumnAlignment {
        LEFT, CENTRE, RIGHT
    }

    var headers = hedings
    var rowValues = generateNestedLists(rowVals)
    var columnAlignments: List<ColumnAlignment>? = colAlignments
    var columnKeyAsValue: List<Boolean>? = colKeyAsValue

    override fun generateString(): String {
        //This code is adpated form other code i wrote for console output..
        // gfm does not require a white spaces to format tables - perhaps colWidth it should be removed
        // (unless the raw markdown should be human readable)

        if (columnKeyAsValue == null) columnKeyAsValue = List<Boolean>(headers.count()) { false }

        var colWidth = 150
        var sb: StringBuilder = StringBuilder()
        val numCols = headers.count()

        sb.append('|')
        for (i in 0 until numCols) {
            sb.append("${colValue(headers[i], colWidth)}|")
        }
        sb.append(System.lineSeparator())
        sb.append('|')
        for (i in 0 until numCols) {
            sb.append("${colValue(getHeaderSeprator(i, colWidth), colWidth)}|")
        }
        sb.append(System.lineSeparator())

        var tableRowIterator = NestedListEnumerator<String>(rowValues)

        for (r in 0 until tableRowIterator.shape.second) {
            var currRow = tableRowIterator.next()
            sb.append('|')
            for (c in 0 until numCols) {
                if (!columnKeyAsValue!![c]) {
                    sb.append("${colValue(ds.getValue(currRow[c]).toString(), colWidth)}|")
                } else {
                    sb.append("${colValue(currRow[c].split('.').last(), colWidth)}|")
                }

                //println(currRow[c])
            }
            sb.append(System.lineSeparator())
        }
        return sb.toString()
    }

    fun getHeaderSeprator(idx: Int, colWidth: Int): String {
        if (columnAlignments == null) {
            return "-".repeat(colWidth)
        } else {
            val alignment = columnAlignments!![idx]
            return when (alignment) {
                ColumnAlignment.LEFT -> ":${"-".repeat(colWidth / 2)}"
                ColumnAlignment.RIGHT -> "${"-".repeat(colWidth / 2)}:"
                ColumnAlignment.CENTRE -> ":${"-".repeat(colWidth / 2)}:"
            }
        }
    }

    fun generateNestedLists(list: List<String>): List<List<String>> {
        var outterList: MutableList<List<String>> = mutableListOf()
        for (l in list) {
            if (ds.isQuery(l)) {
                outterList.add(ds.getQueryValuePaths(l))
            } else {
                outterList.add(MutableList<String>(outterList.first().count()) { l })
            }
        }
        return outterList
    }

    fun colValue(value: String, colWidth: Int): String {
        var valLength = value.length
        var valueToWrite = "";
        if (valLength > colWidth - 4) {
            valueToWrite = value.substring(0, colWidth - 4);
        } else {
            if (valLength % 2 == 0) {
                var numspaces: Int = ((colWidth - 4) - valLength) / 2;
                valueToWrite = "${" ".repeat(numspaces)}$value${" ".repeat(numspaces)}"
            } else {
                var numspaces: Int = ((colWidth - 4) - valLength - 1) / 2;

                valueToWrite = "${" ".repeat(numspaces)}$value${" ".repeat(numspaces)} ";
            }
        }
        return " $valueToWrite ";
    }
}