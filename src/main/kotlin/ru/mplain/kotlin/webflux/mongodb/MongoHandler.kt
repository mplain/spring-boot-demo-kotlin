package ru.mplain.kotlin.webflux.mongodb

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.reactive.awaitSingle
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.find
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.*

@Component
class MongoHandler(
        private val jackson: ObjectMapper,
        factory: ReactiveMongoDatabaseFactory
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val repository = ReactiveMongoTemplate(factory)

    private fun Any.toJson() = jackson.writeValueAsString(this)

    suspend fun post(request: ServerRequest): ServerResponse {
        val body = request.awaitBody<Event>()
        logger.info("POST request received: ${body.toJson()}")
        val result = repository.save(body).awaitSingle()
        return ServerResponse.ok().bodyValueAndAwait(result)
    }

    suspend fun get(request: ServerRequest): ServerResponse {
        val type = request.queryParamOrNull(QUERY_TYPE)
        val page = request.queryParamOrNull(QUERY_PAGE)?.toIntOrNull()?.takeIf { it > 0 } ?: 1
        val size = request.queryParamOrNull(QUERY_SIZE)?.toIntOrNull()?.takeIf { it > 0 } ?: 20
        logger.info("GET request received (type: ${type ?: "any"}, page: ${page}, items per page: ${size})")
        val query = if (type == null) Query() else Query(Criteria.where(QUERY_TYPE).isEqualTo(Event.Type(type)))
        query.with(PageRequest.of(page - 1, size, Sort.by(EVENT_TIME)))
        val result = repository.find<Event>(query)
                .collectMultimap { it.time.toLocalDate() }
                .awaitSingle()
                .toSortedMap()
        logger.info("Retrieving ${result.size} events")
        return if (result.isEmpty()) ServerResponse.notFound().buildAndAwait()
        else ServerResponse.ok().bodyValueAndAwait(result)
    }
}