package com.layababateam.xinxiwang_backend.config

import com.mongodb.ClientSessionOptions
import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.ReadConcern
import com.mongodb.ReadPreference
import com.mongodb.WriteConcern
import com.mongodb.client.ClientSession
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.CreateCollectionOptions
import com.mongodb.event.ConnectionCheckedInEvent
import com.mongodb.event.ConnectionCheckedOutEvent
import com.mongodb.event.ConnectionCheckOutFailedEvent
import com.mongodb.event.ConnectionCheckOutStartedEvent
import com.mongodb.event.ConnectionClosedEvent
import com.mongodb.event.ConnectionCreatedEvent
import com.mongodb.event.ConnectionPoolClearedEvent
import com.mongodb.event.ConnectionPoolClosedEvent
import com.mongodb.event.ConnectionPoolCreatedEvent
import com.mongodb.event.ConnectionPoolListener
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.bson.Document
import org.bson.codecs.configuration.CodecRegistry
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.dao.DataAccessException
import org.springframework.dao.support.PersistenceExceptionTranslator
import org.springframework.data.mongodb.MongoDatabaseFactory
import org.springframework.data.mongodb.MongoTransactionManager
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory
import org.springframework.transaction.annotation.EnableTransactionManagement
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@Configuration
@EnableTransactionManagement
class MongoConfig(
    @Value("\${spring.data.mongodb.uri}") private val uri: String,
    @Value("\${app.environment:\${sentry.environment:production}}") private val appEnvironment: String,
    @Value("\${xinxiwang.mongo.collection-prefix:}") private val configuredCollectionPrefix: String,
    private val meterRegistry: MeterRegistry,
) {
    @Bean
    fun mongoClient(): MongoClient {
        val poolListener = MongoPoolMetrics(meterRegistry)
        val settings = MongoClientSettings.builder()
            .applyConnectionString(ConnectionString(uri))
            .applyToConnectionPoolSettings { pool ->
                pool.maxSize(200)
                pool.minSize(10)
                pool.maxWaitTime(3000, TimeUnit.MILLISECONDS)
                pool.maxConnectionIdleTime(60000, TimeUnit.MILLISECONDS)
                pool.addConnectionPoolListener(poolListener)
            }
            .applyToSocketSettings { socket ->
                socket.connectTimeout(5000, TimeUnit.MILLISECONDS)
                socket.readTimeout(30000, TimeUnit.MILLISECONDS)
            }
            .build()
        return MongoClients.create(settings)
    }

    @Bean
    fun mongoDatabaseFactory(mongoClient: MongoClient): MongoDatabaseFactory {
        val databaseName = ConnectionString(uri).database?.takeIf { it.isNotBlank() } ?: "xinxiwang"
        val factory = SimpleMongoClientDatabaseFactory(mongoClient, databaseName)
        val collectionPrefix = InfrastructureNamespaces.effectiveMongoCollectionPrefix(
            appEnvironment,
            configuredCollectionPrefix,
        )
        return if (collectionPrefix.isBlank()) factory else PrefixingMongoDatabaseFactory(factory, collectionPrefix)
    }

    @Bean
    fun transactionManager(dbFactory: MongoDatabaseFactory): MongoTransactionManager {
        return MongoTransactionManager(dbFactory)
    }
}

private class PrefixingMongoDatabaseFactory(
    private val delegate: MongoDatabaseFactory,
    private val collectionPrefix: String,
) : MongoDatabaseFactory {
    @Throws(DataAccessException::class)
    override fun getMongoDatabase(): MongoDatabase =
        PrefixingMongoDatabase(delegate.mongoDatabase, collectionPrefix)

    @Throws(DataAccessException::class)
    override fun getMongoDatabase(dbName: String): MongoDatabase =
        PrefixingMongoDatabase(delegate.getMongoDatabase(dbName), collectionPrefix)

    override fun getExceptionTranslator(): PersistenceExceptionTranslator =
        delegate.exceptionTranslator

    override fun getSession(options: ClientSessionOptions): ClientSession =
        delegate.getSession(options)

    override fun withSession(session: ClientSession): MongoDatabaseFactory =
        PrefixingMongoDatabaseFactory(delegate.withSession(session), collectionPrefix)

    override fun isTransactionActive(): Boolean =
        delegate.isTransactionActive
}

private class PrefixingMongoDatabase(
    private val delegate: MongoDatabase,
    private val collectionPrefix: String,
) : MongoDatabase by delegate {
    override fun getCollection(collectionName: String): MongoCollection<Document> =
        delegate.getCollection(prefixed(collectionName))

    override fun <TDocument : Any?> getCollection(
        collectionName: String,
        documentClass: Class<TDocument>,
    ): MongoCollection<TDocument> =
        delegate.getCollection(prefixed(collectionName), documentClass)

    override fun createCollection(collectionName: String) =
        delegate.createCollection(prefixed(collectionName))

    override fun createCollection(collectionName: String, collectionOptions: CreateCollectionOptions) =
        delegate.createCollection(prefixed(collectionName), collectionOptions)

    override fun createCollection(clientSession: ClientSession, collectionName: String) =
        delegate.createCollection(clientSession, prefixed(collectionName))

    override fun createCollection(
        clientSession: ClientSession,
        collectionName: String,
        collectionOptions: CreateCollectionOptions,
    ) = delegate.createCollection(clientSession, prefixed(collectionName), collectionOptions)

    override fun withCodecRegistry(codecRegistry: CodecRegistry): MongoDatabase =
        PrefixingMongoDatabase(delegate.withCodecRegistry(codecRegistry), collectionPrefix)

    override fun withReadPreference(readPreference: ReadPreference): MongoDatabase =
        PrefixingMongoDatabase(delegate.withReadPreference(readPreference), collectionPrefix)

    override fun withWriteConcern(writeConcern: WriteConcern): MongoDatabase =
        PrefixingMongoDatabase(delegate.withWriteConcern(writeConcern), collectionPrefix)

    override fun withReadConcern(readConcern: ReadConcern): MongoDatabase =
        PrefixingMongoDatabase(delegate.withReadConcern(readConcern), collectionPrefix)

    override fun withTimeout(timeout: Long, timeUnit: TimeUnit): MongoDatabase =
        PrefixingMongoDatabase(delegate.withTimeout(timeout, timeUnit), collectionPrefix)

    private fun prefixed(collectionName: String): String =
        InfrastructureNamespaces.prefixed(collectionPrefix, collectionName)
}

class MongoPoolMetrics(registry: MeterRegistry) : ConnectionPoolListener {

    private val totalCreated = AtomicInteger(0)
    private val checkedOut = AtomicInteger(0)
    private val waitQueue = AtomicInteger(0)

    init {
        Gauge.builder("mongodb.driver.pool.size", totalCreated) { it.toDouble() }
            .description("Current size of the MongoDB connection pool")
            .register(registry)
        Gauge.builder("mongodb.driver.pool.checkedout", checkedOut) { it.toDouble() }
            .description("Number of connections currently checked out from the pool")
            .register(registry)
        Gauge.builder("mongodb.driver.pool.waitqueuesize", waitQueue) { it.toDouble() }
            .description("Number of threads waiting for a connection from the pool")
            .register(registry)
    }

    override fun connectionPoolCreated(event: ConnectionPoolCreatedEvent) {}

    override fun connectionPoolCleared(event: ConnectionPoolClearedEvent) {
        totalCreated.set(0)
        checkedOut.set(0)
    }

    override fun connectionPoolClosed(event: ConnectionPoolClosedEvent) {
        totalCreated.set(0)
        checkedOut.set(0)
    }

    override fun connectionCreated(event: ConnectionCreatedEvent) {
        totalCreated.incrementAndGet()
    }

    override fun connectionClosed(event: ConnectionClosedEvent) {
        totalCreated.decrementAndGet()
    }

    override fun connectionCheckOutStarted(event: ConnectionCheckOutStartedEvent) {
        waitQueue.incrementAndGet()
    }

    override fun connectionCheckedOut(event: ConnectionCheckedOutEvent) {
        checkedOut.incrementAndGet()
        waitQueue.decrementAndGet()
    }

    override fun connectionCheckOutFailed(event: ConnectionCheckOutFailedEvent) {
        waitQueue.decrementAndGet()
    }

    override fun connectionCheckedIn(event: ConnectionCheckedInEvent) {
        checkedOut.decrementAndGet()
    }
}
