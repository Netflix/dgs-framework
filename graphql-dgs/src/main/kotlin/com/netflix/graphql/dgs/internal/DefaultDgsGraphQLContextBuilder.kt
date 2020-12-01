package com.netflix.graphql.dgs.internal

import com.netflix.graphql.dgs.DgsContextBuilder
import com.netflix.graphql.dgs.context.DgsContext
import com.netflix.graphql.dgs.context.DgsCustomContextBuilder
import com.netflix.graphql.dgs.internal.utils.TimeTracer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import java.util.*


open class DefaultDgsGraphQLContextBuilder(private val dgsCustomContextBuilder: Optional<DgsCustomContextBuilder<*>>) : DgsContextBuilder {
    val logger: Logger = LoggerFactory.getLogger(DefaultDgsGraphQLContextBuilder::class.java)
    var extensions: Map<String, Any>? = null
    var headers: HttpHeaders? = null

    override fun build(): DgsContext {
        return TimeTracer.logTime({buildDgsContext()}, logger, "Created DGS context in {}ms")
    }

    private fun buildDgsContext(): DgsContext {
        val customContext = if (dgsCustomContextBuilder.isPresent)
            dgsCustomContextBuilder.get().build()
        else
            DefaultRequestData(extensions ?: mapOf(), headers ?: HttpHeaders())
        return DgsContext(customContext)
    }
}

data class DefaultRequestData(val extensions: Map<String, Any>, val headers: HttpHeaders)
