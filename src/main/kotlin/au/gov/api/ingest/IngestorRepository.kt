package au.gov.api.ingest

import com.fasterxml.jackson.databind.ObjectMapper
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Bean
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.sql.Connection
import java.sql.SQLException
import javax.sql.DataSource

class RepositoryException() : RuntimeException()

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
        var manifests = findAll()
        var pipelines:MutableList<PipelineBuilder> = mutableListOf()
        manifests.forEach { pipelines.add(PipelineBuilder(it)) }
        pipelines.forEach { it.buildPipeline(PipelineBuilder.AssetMechanism.poll) }
        pipelines.forEach { it.executePipes() }
    }
    /*@EventListener(ApplicationReadyEvent::class)
    private fun previewManifest() {
        var example = PipelineBuilder(PipelineBuilder.getTextOfURL("file:///C:/Users/theza/Desktop/test.json"))
        example.buildPipeline()
        example.Pipes.first().finaliseData()
        example.Pipes.first().finaliseEngine()
        example = example
    }*/
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

    fun findById(id: String):Manifest {
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

    fun findAll() : Iterable<Manifest> {
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
