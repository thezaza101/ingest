package au.gov.api.ingest.converters.models

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import java.io.File
import java.util.*
import kotlin.collections.LinkedHashMap

class ObjectDocument : DataSource {
    var docString: String = ""
    var doc: LinkedHashMap<String, Any> = linkedMapOf()

    constructor(document: String, isYAML: Boolean = true) {
        if (isYAML) {
            docString = convertYamlToJson(document)!!
        } else {
            docString = document
        }
        doc = ObjectMapper().readValue(docString, LinkedHashMap::class.java) as LinkedHashMap<String, Any>
    }

    constructor(stDoc: LinkedHashMap<String, Any>) {
        doc = stDoc
    }

    override fun getValue(path: String): Any {
        return getNestedValue(ArrayDeque<String>(path.substring(1).split('.')), doc)
    }

    override fun isPath(path: String): Boolean {
        return path.startsWith('.')
    }

    fun getNestedValue(paths: Queue<String>, innerDoc: LinkedHashMap<String, Any>): Any {
        var currentKey = paths.peek()

        if (currentKey != null) {
            if (innerDoc.containsKey(currentKey)) {
                var currDoc = innerDoc[currentKey]

                if (currDoc is Map<*, *>) {
                    paths.remove(currentKey)
                    return getNestedValue(paths, currDoc as LinkedHashMap<String, Any>)
                } else if (currDoc is List<*>) {
                    paths.remove(currentKey)
                    var indexKey = paths.peek()
                    if (indexKey != null) {
                        if (indexKey.startsWith('[') && indexKey.endsWith(']')) {
                            var index =
                                    "\\[(.*?)\\]".toRegex().find(indexKey)!!.value.substring(1, indexKey.count() - 1)
                            paths.remove(indexKey)
                            if (index == "*") return currDoc!!
                            var nestedObj = (currDoc as List<*>)[index.toInt()]
                            return when (nestedObj is Map<*, *>) {
                                true -> getNestedValue(paths, nestedObj as LinkedHashMap<String, Any>)
                                false -> nestedObj!!
                            }
                        } else {
                            return currDoc!!
                        }
                    } else {
                        return currDoc!!
                    }
                } else {
                    return currDoc!!
                }
            } else {
                //return innerDoc.values
                throw Exception("Key \"" + currentKey + "\" not Found")
            }
        } else {
            return innerDoc
        }
    }

    override fun getQueryValuePaths(query: String): List<String> {
        var v = ArrayDeque<String>(query.substring(1).split('.'))
        var paths: MutableList<String> = mutableListOf()
        do {
            var currIdx = v.pop()!!

            if (currIdx.startsWith('[') && currIdx.endsWith(']')) {
                var index = "\\[(.*?)\\]".toRegex().find(currIdx)!!.value.substring(1, currIdx.count() - 1)
                if (index == "*") {
                    var newPaths: MutableList<String> = mutableListOf()
                    for (p in paths) {
                        var cDoc: Any = getValue(p)
                        if (cDoc is Map<*, *>) {
                            for (k in cDoc.keys) {
                                newPaths.add("$p.${k.toString()}")
                            }
                        } else if (cDoc is List<*>) {
                            for (k in 0 until cDoc.count()) {
                                newPaths.add("$p.[$k]")
                            }
                        }
                    }
                    paths = newPaths
                }
            } else {
                var newPaths: MutableList<String> = mutableListOf()
                if (paths.count() >= 1) paths.forEach { newPaths.add("$it.$currIdx") } else newPaths.add(".$currIdx")
                paths = newPaths
            }
        } while (v.peek() != null)

        return paths
    }

    override fun isQuery(query: String): Boolean {
        return query.startsWith('.')
    }

    fun convertYamlToJson(yaml: String?): String? {
        val yamlReader = ObjectMapper(YAMLFactory())
        val obj: Any = yamlReader.readValue(yaml, Any::class.java)
        val jsonWriter = ObjectMapper()
        return jsonWriter.writeValueAsString(obj)
    }

    companion object {
        fun readFileAsText(fileName: String): String = File(fileName).readText(Charsets.UTF_8)
    }


}