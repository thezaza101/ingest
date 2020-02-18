package au.gov.api.ingest.converters.formats.markdown

import au.gov.api.ingest.converters.models.DataSource

class MarkdownTextGenerator(
        str: String,
        fmt: MDTextFormats,
        ds: DataSource,
        newLine: Boolean = true,
        fmtStr: String = ""
) : MarkdownGenerator(ds) {
    var inputString = str
    val format = fmt
    val nl = newLine
    val formatString = fmtStr

    override fun generateString(): String {
        if (ds.isPath(inputString)) inputString = ds.getValue(inputString) as String
        return if (format == MDTextFormats.USERDEFINED) formatText(
                inputString,
                format,
                nl,
                formatString
        ) else formatText(inputString, format, nl)
    }
}