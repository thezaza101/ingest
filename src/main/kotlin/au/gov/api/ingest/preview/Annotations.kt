package au.gov.api.ingest.preview


@Target(AnnotationTarget.CLASS)
annotation class EngineImpl(val SupportedDataType: String, val HelpText: String)