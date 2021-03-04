/*
 * Copyright 2021 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
        return TimeTracer.logTime({ buildDgsContext() }, logger, "Created DGS context in {}ms")
    }

    private fun buildDgsContext(): DgsContext {
        val customContext = if (dgsCustomContextBuilder.isPresent)
            dgsCustomContextBuilder.get().build()
        else
        //This is for backwards compatibility - we previously made DefaultRequestData the custom context if no custom context was provided.
            DefaultRequestData(extensions ?: mapOf(), headers ?: HttpHeaders())

        return DgsContext(customContext, DefaultRequestData(extensions ?: mapOf(), headers ?: HttpHeaders()))
    }
}

data class DefaultRequestData(val extensions: Map<String, Any>, val headers: HttpHeaders)
