package au.gov.api.ingest.converters.swaggerToMarkdown

import au.gov.api.ingest.converters.formats.markdown.*
import au.gov.api.ingest.converters.models.DataSource
import au.gov.api.ingest.converters.models.ObjectDocument
import kotlin.math.absoluteValue

class SwaggerToMarkdownConverter(document: ObjectDocument, config: ObjectDocument) {
    var ds = document
    var cfg = config

    fun convert(): String = generateMarkdownConfig(cfg, ds).generateString()

    fun generateMarkdownConfig(config: ObjectDocument, dataSource: DataSource): MarkdownGenerator {
        var dc = config.getValue(".documentConfig") as List<LinkedHashMap<String, *>>
        //var path = config.getValue(".documentConfig.path") as String
        var doc = MarkdownSection("", listOf(), dataSource)
        dc.forEach { doc.innerMDG.add(processMarkdownSection(it, "", dataSource)) }
        return doc
    }

    private fun processMarkdownSection(
            sectionDef: LinkedHashMap<String, *>,
            parentPath: String,
            dataSource: DataSource
    ): MarkdownGenerator {
        var mds: MarkdownSection = MarkdownSection(parentPath, listOf(), dataSource)
        var innerSections = sectionDef["innerSections"] as List<LinkedHashMap<String, *>>
        innerSections.forEach { mds.innerMDG.add(generateMarkdownElement(it, parentPath, dataSource)) }
        return mds
    }

    private fun generateMarkdownElement(
            elementDef: LinkedHashMap<String, *>,
            parentPath: String,
            dataSource: DataSource
    ): MarkdownGenerator {
        var element: MarkdownGenerator
        var elementName = (elementDef["name"] as String).toUpperCase()

        when (elementName) {
            "TEXT" -> {
                var value = (elementDef["value"] as String)
                if (dataSource.isPath(value)) value = "$parentPath$value"

                if (value.startsWith("\$KEY")) {
                    var splitter = value.substring(value.indexOf('[') + 1, value.indexOf('[') + 2)
                    var idx = value.substring(value.lastIndexOf('[') + 1, value.lastIndexOf(']')).toInt()
                    var keySplit = parentPath.split(splitter)
                    var key = ""
                    if (idx >= 0) {
                        key = keySplit[idx + 1]
                    } else {
                        key = keySplit.reversed()[idx.absoluteValue - 1]
                    }
                    value = key
                }

                element = MarkdownTextGenerator(
                        value,
                        MDTextFormats.valueOf((elementDef["format"] as String)),
                        dataSource,
                        (elementDef["newLine"] as Boolean),
                        (elementDef["formatString"] as String)
                )
            }
            "TABLE" -> {
                var headings = elementDef["headings"] as List<String>
                var values = (elementDef["values"] as MutableList<String>).toMutableList()
                for (i in 0 until values.count()) {
                    if (!values[i].startsWith(parentPath)) {
                        values[i] = "$parentPath${values[i]}"
                    }
                }
                var colAlignments: MutableList<MarkdownTableGenerator.ColumnAlignment> = mutableListOf()
                (elementDef["colAlignments"] as List<String>).forEach { colAlignments.add(MarkdownTableGenerator.ColumnAlignment.valueOf(it)) }
                var colAsKeys = elementDef["colAsKeys"] as List<Boolean>
                element = MarkdownTableGenerator(headings, values, dataSource, colAlignments, colAsKeys)
            }
            "LIST" -> {
                var value = "$parentPath${elementDef["value"] as String}"
                var listType = MarkdownListGenerator.ListType.valueOf(elementDef["listType"] as String)
                element = MarkdownListGenerator(value, dataSource, listType)
            }
            "MARKDOWNSECTION" -> {
                element = MarkdownSection(parentPath, listOf(), dataSource)
                var path = elementDef["path"] as String
                var innerSections = (elementDef["innerSections"] as List<LinkedHashMap<String, *>>)
                innerSections.forEach {
                    (element as MarkdownSection).innerMDG.add(
                            processMarkdownSection(
                                    it,
                                    path,
                                    dataSource
                            )
                    )
                }
            }
            "MARKDOWNSECTIONS" -> {
                element = MarkdownSection(parentPath, listOf(), dataSource)
                var paths = dataSource.getQueryValuePaths(elementDef["path"] as String)
                var innerSections = (elementDef["innerSections"] as List<LinkedHashMap<String, *>>)

                for (i in 0 until paths.count()) {
                    innerSections.forEach {
                        (element as MarkdownSection).innerMDG.add(generateMarkdownElement(it, paths[i], dataSource))
                    }
                }
            }
            else -> element = MarkdownSection("", listOf(), dataSource)
        }
        return element
    }
}