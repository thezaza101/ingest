package au.gov.api.ingest.preview

@Target(AnnotationTarget.CLASS)
annotation class DataImpl(val SupportedIngestType: String, val HelpText: String)

@Target(AnnotationTarget.CLASS)
annotation class EngineImpl(val SupportedDataType: String, val OutputType:String, val HelpText: String)

@Target(AnnotationTarget.CLASS)
annotation class IngestImpl(val SupportedIngestType: String, val HelpText: String)