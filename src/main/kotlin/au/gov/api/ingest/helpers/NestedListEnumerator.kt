package au.gov.api.ingest.helpers

class NestedListEnumerator<T>(val nestedList: List<List<T>>) : Iterator<List<T>> {

    var innerList = nestedList
    var idx = 0

    var shape = Pair(innerList.count(), innerList.first().count())

    override fun hasNext(): Boolean {
        return innerList.first().count() <= idx
    }

    override fun next(): List<T> {
        var output: MutableList<T> = mutableListOf()
        innerList.forEach { output.add(it[idx]) }
        idx++
        return output
    }
}