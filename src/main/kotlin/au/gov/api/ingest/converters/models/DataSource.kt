package au.gov.api.ingest.converters.models

abstract class DataSource {
    //isPath validates if the input string can be a path to a value (it does not validate if the value exists)
    abstract fun isPath(path: String): Boolean

    //getValue gets a value based on the supplied paths
    abstract fun getValue(path: String): Any

    //isQuery validates if the input string can be a query parsable by the data source
    abstract fun isQuery(query: String): Boolean

    //getCollection retuns a collection of paths
    abstract fun getQueryValuePaths(query: String): List<String>
}