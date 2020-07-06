package au.gov.api.ingest.converters.formats.markdown

import au.gov.api.ingest.converters.models.DataSource
import com.fasterxml.jackson.annotation.JsonIgnore


abstract class MarkdownGenerator(source: DataSource) {
    @JsonIgnore
    open var ds = source

    abstract fun generateString(): String

    fun formatText(text: String, fmt: MDTextFormats, newLine: Boolean = true, userFormat: String = ""): String {
        var nl = if (newLine) System.lineSeparator() else ""
        return when (fmt) {
            MDTextFormats.NONE -> "$text$nl"
            MDTextFormats.BOLD -> "__${text}__$nl"
            MDTextFormats.ITALIC -> "_${text}_$nl"
            MDTextFormats.STRIKE -> "~~$text~~$nl"
            MDTextFormats.QUOTE -> "> $text$nl"
            MDTextFormats.CODE -> "```$text```$nl"
            MDTextFormats.H1 -> "# $text$nl"
            MDTextFormats.H2 -> "## $text$nl"
            MDTextFormats.H3 -> "### $text$nl"
            MDTextFormats.H4 -> "#### $text$nl"
            MDTextFormats.H5 -> "##### $text$nl"
            MDTextFormats.H6 -> "###### $text$nl"
            MDTextFormats.USERDEFINED -> "${userFormat.replace("%", text)}$nl"
            else -> "$text$nl"
        }
    }
}