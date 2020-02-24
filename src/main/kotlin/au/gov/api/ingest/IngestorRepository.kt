package au.gov.api.ingest

import au.gov.api.ingest.preview.DataImpl
import au.gov.api.ingest.preview.EngineImpl
import au.gov.api.ingest.preview.IngestImpl
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.context.event.EventListener
import org.springframework.core.type.filter.AnnotationTypeFilter
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.io.File
import java.sql.Connection
import javax.sql.DataSource

class RepositoryException() : RuntimeException()

data class FileData(val id: String, val timestamp: String, val fileName: String, val MIMEType: String, val content: String = "")

@Service
class IngestorRepository {

    @Autowired
    lateinit var dataSource: DataSource

    constructor() {}

    constructor(theDataSource: DataSource) {
        dataSource = theDataSource
    }

    @Scheduled(fixedRate = 1800000)
    fun runScheduledPollEvents() {
        println("Executing scheduled tasks...")
        var manifests = findAll()
        var pipelines: MutableList<PipelineBuilder> = mutableListOf()
        manifests.forEach { pipelines.add(PipelineBuilder(it)) }
        pipelines.forEach { it.buildPipeline(PipelineBuilder.AssetMechanism.poll, this) }
        pipelines.forEach { it.executePipes() }
    }

    @EventListener(ApplicationReadyEvent::class)
    private fun updateAPIMetadata() {
        var results = hashMapOf<String, HashMap<String, String>>()
        var data = hashMapOf<String, String>()
        var engines = hashMapOf<String, String>()
        var ingestors = hashMapOf<String, String>()
        var packageName = "au.gov.api.ingest"

        var scannerData = ClassPathScanningCandidateComponentProvider(false)
        scannerData.addIncludeFilter(AnnotationTypeFilter(DataImpl::class.java))
        scannerData.findCandidateComponents(packageName)
                .forEach {
                    data.put(it.beanClassName.split('.').last().replace("Data", ""),
                            annotationStringToJson(Class.forName(it.beanClassName).annotations.filter { it is DataImpl }.first().toString()))
                }


        var scannerEngines = ClassPathScanningCandidateComponentProvider(false)
        scannerEngines.addIncludeFilter(AnnotationTypeFilter(EngineImpl::class.java))
        scannerEngines.findCandidateComponents(packageName)
                .forEach {
                    engines.put(it.beanClassName.split('.').last().replace("Engine", ""),
                            annotationStringToJson(Class.forName(it.beanClassName).annotations.filter { it is EngineImpl }.first().toString()))
                }

        var scannerIngestors = ClassPathScanningCandidateComponentProvider(false)
        scannerIngestors.addIncludeFilter(AnnotationTypeFilter(IngestImpl::class.java))
        scannerIngestors.findCandidateComponents(packageName)
                .forEach {
                    ingestors.put(it.beanClassName.split('.').last().replace("Ingestor", ""),
                            annotationStringToJson(Class.forName(it.beanClassName).annotations.filter { it is IngestImpl }.first().toString()))
                }


        results.put("Data", data)
        results.put("Engine", engines)
        results.put("Ingestors", ingestors)

        var json = ObjectMapper().writeValueAsString(results).replace("\\", "").replace("\"{", "{").replace("}\"", "}")
        File("meta.json").delete()
        File("meta.json").writeText(json)
    }

    fun annotationStringToJson(annotationString: String): String {
        var annotDetails = annotationString.split('.').last()
        annotDetails = annotDetails.removeRange(0, annotDetails.indexOf('(') + 1)
        annotDetails = annotDetails.take(annotDetails.length - 1)
        var output = "{"
        annotDetails.split(',').forEach {
            val both = it.split('=')
            output = "$output \"${both.first().trim()}\" : \"${both.last().trim()}\","
        }
        return "${output.take(output.length - 1)} }"
    }
    /*@EventListener(ApplicationReadyEvent::class)
    private fun previewManifest() {
        var example = PipelineBuilder(PipelineBuilder.getTextOfURL("file:///C:/Users/theza/Desktop/test.json"))
        example.buildPipeline()
        example.Pipes.first().finaliseData()
        example.Pipes.first().finaliseEngine()
        example = example
    }*/

    fun saveFile(md5: String, fileName: String, type: String, key: String, fileString: String) {
        var connection: Connection? = null
        try {
            connection = dataSource.connection
            val stmt = connection.createStatement()
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS ingest_files (id VARCHAR(40), timestamp VARCHAR(20), filename VARCHAR(256), type VARCHAR(256), key VARCHAR(50), data text,  PRIMARY KEY (id))")
            val upsert = connection.prepareStatement("INSERT INTO ingest_files(id, timestamp, filename, type, key, data) VALUES(?, ?, ?, ?, ?, ?) ON CONFLICT(id) DO UPDATE SET data = EXCLUDED.data")
            upsert.setString(1, md5)
            upsert.setString(2, (System.currentTimeMillis() / 1000).toString())
            upsert.setString(3, fileName)
            upsert.setString(4, type)
            upsert.setString(5, key)
            upsert.setString(6, fileString)
            upsert.executeUpdate()
        } catch (e: Exception) {
            e.printStackTrace()
            throw RepositoryException()
        } finally {
            if (connection != null) connection.close()
        }
    }

    fun findFileByKey(key: String): List<FileData> {
        var connection: Connection? = null
        try {
            connection = dataSource.connection

            val q = connection.prepareStatement("SELECT * FROM ingest_files WHERE key = ?")
            q.setString(1, key)
            val rv: MutableList<FileData> = mutableListOf()
            var rs = q.executeQuery()
            while (rs.next()) {
                rv.add(FileData(rs.getString("id"),
                        rs.getString("timestamp"),
                        rs.getString("filename"),
                        rs.getString("type"),
                        "${rs.getString("data").toByteArray().size} bytes"))
            }

            return rv
        } catch (e: Exception) {
            e.printStackTrace()
            throw RepositoryException()
        } finally {
            if (connection != null) connection.close()
        }
    }

    fun findFileById(id: String): FileData {
        var connection: Connection? = null
        try {
            connection = dataSource.connection

            val q = connection.prepareStatement("SELECT * FROM ingest_files WHERE id = ?")
            q.setString(1, id)
            var rs = q.executeQuery()
            if (!rs.next()) {
                throw RepositoryException()
            }
            return FileData(id,
                    rs.getString("timestamp"),
                    rs.getString("filename"),
                    rs.getString("type"),
                    java.util.Base64.getDecoder().decode(
                            rs.getString("data").toByteArray()).toString(charset = Charsets.UTF_8))

        } catch (e: Exception) {
            e.printStackTrace()
            throw RepositoryException()
        } finally {
            if (connection != null) connection.close()
        }
    }


    fun deleteFile(id: String) {
        var connection: Connection? = null
        try {
            connection = dataSource.connection

            val q = connection.prepareStatement("DELETE FROM ingest_files WHERE id = ?")
            q.setString(1, id)
            q.executeUpdate()
        } catch (e: Exception) {
            e.printStackTrace()
            throw RepositoryException()
        } finally {
            if (connection != null) connection.close()
        }
    }

    fun save(manifest: Manifest) {
        var connection: Connection? = null
        try {
            connection = dataSource.connection
            val stmt = connection.createStatement()
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS ingest_manifests (id VARCHAR(50), data JSONB, PRIMARY KEY (id))")
            val upsert = connection.prepareStatement("INSERT INTO ingest_manifests(id, data) VALUES(?, ?::jsonb) ON CONFLICT(id) DO UPDATE SET data = EXCLUDED.data")
            upsert.setString(1, manifest.metadata.id)
            upsert.setString(2, ObjectMapper().writeValueAsString(manifest))
            upsert.executeUpdate()
        } catch (e: Exception) {
            e.printStackTrace()
            throw RepositoryException()
        } finally {
            if (connection != null) connection.close()
        }
    }

    fun findById(id: String): Manifest {
        var connection: Connection? = null
        try {
            connection = dataSource.connection

            val q = connection.prepareStatement("SELECT data FROM ingest_manifests WHERE id = ?")
            q.setString(1, id)
            var rs = q.executeQuery()
            if (!rs.next()) {
                throw RepositoryException()
            }
            val sd = ObjectMapper().readValue(rs.getString("data"), Manifest::class.java)
            return sd

        } catch (e: Exception) {
            e.printStackTrace()
            throw RepositoryException()
        } finally {
            if (connection != null) connection.close()
        }
    }

    fun findAll(): Iterable<Manifest> {
        var connection: Connection? = null
        try {
            connection = dataSource.connection

            val stmt = connection.createStatement()
            val rs = stmt.executeQuery("SELECT data FROM ingest_manifests")
            val rv: MutableList<Manifest> = mutableListOf()
            val om = ObjectMapper()
            while (rs.next()) {
                rv.add(om.readValue(rs.getString("data"), Manifest::class.java))
            }
            return rv

        } catch (e: Exception) {
            e.printStackTrace()
            throw RepositoryException()
        } finally {
            if (connection != null) connection.close()
        }
    }

    fun delete(id: String) {
        var connection: Connection? = null
        try {
            connection = dataSource.connection

            val q = connection.prepareStatement("DELETE FROM ingest_manifests WHERE id = ?")
            q.setString(1, id)
            q.executeUpdate()
        } catch (e: Exception) {
            e.printStackTrace()
            throw RepositoryException()
        } finally {
            if (connection != null) connection.close()
        }
    }


}
