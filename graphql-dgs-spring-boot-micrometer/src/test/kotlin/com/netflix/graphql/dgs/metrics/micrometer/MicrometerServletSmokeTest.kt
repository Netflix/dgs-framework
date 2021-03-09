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

package com.netflix.graphql.dgs.metrics.micrometer

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsData
import com.netflix.graphql.dgs.DgsDataLoader
import com.netflix.graphql.dgs.DgsTypeDefinitionRegistry
import com.netflix.graphql.dgs.exceptions.DefaultDataFetcherExceptionHandler
import com.netflix.graphql.dgs.exceptions.DgsBadRequestException
import com.netflix.graphql.dgs.metrics.micrometer.tagging.DgsContextualTagCustomizer
import com.netflix.graphql.dgs.metrics.micrometer.tagging.DgsExecutionTagCustomizer
import com.netflix.graphql.dgs.metrics.micrometer.tagging.DgsFieldFetchTagCustomizer
import com.netflix.graphql.types.errors.TypedGraphQLError
import graphql.GraphQLError
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.DataFetcherExceptionHandlerParameters
import graphql.execution.DataFetcherExceptionHandlerResult
import graphql.language.FieldDefinition
import graphql.language.ObjectTypeDefinition
import graphql.language.TypeName
import graphql.schema.DataFetchingEnvironment
import graphql.schema.idl.TypeDefinitionRegistry
import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.`as`
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.InstanceOfAssertFactories
import org.dataloader.BatchLoader
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.concurrent.CompletableFuture

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    classes = [MicrometerServletSmokeTest.LocalApp::class, MicrometerServletSmokeTest.LocalTestConfiguration::class]
)
@EnableAutoConfiguration
@AutoConfigureMockMvc
class MicrometerServletSmokeTest {

    private val asTags = `as`(InstanceOfAssertFactories.iterable(Tag::class.java))

    @Autowired
    lateinit var meterRegistry: MeterRegistry

    @Autowired
    lateinit var mvc: MockMvc

    @BeforeEach
    fun resetMeterRegistry() {
        meterRegistry.clear()
    }

    @Test
    fun `Assert the type of MeterRegistry we require for the test`() {
        assertThat(meterRegistry).isNotNull
        assertThat(meterRegistry).isExactlyInstanceOf(SimpleMeterRegistry::class.java)
    }

    @Test
    fun `Metrics for a successful request`() {
        mvc.perform(
            MockMvcRequestBuilders
                .post("/graphql")
                .content("""{ "query": "{ping}" }""")
        ).andExpect(status().isOk)
            .andExpect(content().json("""{"data":{"ping":"pong"}}""", false))

        val meters = qglMeters()

        assertThat(meters).containsOnlyKeys("gql.query", "gql.resolver")

        assertThat(meters["gql.query"]).isNotNull
            .extracting({ it?.id?.tags }, asTags)
            .containsExactlyInAnyOrderElementsOf(
                Tags.of("execution-tag", "foo")
                    .and("contextual-tag", "foo")
                    .and("outcome", "SUCCESS")
            )

        assertThat(meters["gql.resolver"]).isNotNull
            .extracting({ it?.id?.tags }, asTags)
            .containsExactlyInAnyOrderElementsOf(
                Tags.of("field-fetch-tag", "foo")
                    .and("contextual-tag", "foo")
                    .and("gql.field", "Query.ping")
                    .and("outcome", "SUCCESS")
            )
    }

    @Test
    fun `Metrics for a successful request with data loaders`() {
        mvc.perform(
            MockMvcRequestBuilders
                .post("/graphql")
                .content("""{ "query": "{upperCased}" }""")
        ).andExpect(status().isOk)
            .andExpect(content().json("""{"data":{"upperCased":"[A, B, C]"}}""", false))

        val meters = qglMeters()

        assertThat(meters).containsOnlyKeys("gql.dataLoader", "gql.query", "gql.resolver")

        assertThat(meters["gql.dataLoader"]).isNotNull
            .extracting({ it?.id?.tags }, asTags)
            .containsExactlyInAnyOrderElementsOf(
                Tags.of("gql.loaderBatchSize", "3")
                    .and("gql.loaderName", "upperCaseLoader")
            )

        assertThat(meters["gql.query"]).isNotNull
            .extracting({ it?.id?.tags }, asTags)
            .containsExactlyInAnyOrderElementsOf(
                Tags.of("execution-tag", "foo")
                    .and("contextual-tag", "foo")
                    .and("outcome", "SUCCESS")
            )

        assertThat(meters["gql.resolver"]).isNotNull
            .extracting({ it?.id?.tags }, asTags)
            .containsExactlyInAnyOrderElementsOf(
                Tags.of("field-fetch-tag", "foo")
                    .and("contextual-tag", "foo")
                    .and("gql.field", "Query.upperCased")
                    .and("outcome", "SUCCESS")
            )
    }

    @Test
    fun `Assert metrics for a syntax error`() {
        mvc.perform(
            MockMvcRequestBuilders
                .post("/graphql")
                .content("""{ "query": "fail" }""")
        ).andExpect(status().isOk)
            .andExpect(
                content().json(
                    """
                        |{
                        |   "errors":[
                        |       {"message":"Invalid Syntax : offending token 'fail' at line 1 column 1",
                        |           "locations":[{"line":1,"column":1}],"extensions":{"classification":"InvalidSyntax"}}
                        |   ]
                        |}""".trimMargin(),
                    false
                )
            )
        val meters = qglMeters()

        assertThat(meters).containsOnlyKeys("gql.error", "gql.query")

        assertThat(meters["gql.error"]).isNotNull
            .extracting({ it?.id?.tags }, asTags)
            .containsExactlyInAnyOrderElementsOf(
                Tags.of("execution-tag", "foo")
                    .and("contextual-tag", "foo")
                    .and("gql.errorDetail", "none")
                    .and("gql.errorCode", "InvalidSyntax")
                    .and("gql.path", "[]")
                    .and("outcome", "ERROR")
            )

        assertThat(meters["gql.query"]).isNotNull
            .extracting({ it?.id?.tags }, asTags)
            .containsExactlyInAnyOrderElementsOf(
                Tags.of("execution-tag", "foo")
                    .and("contextual-tag", "foo")
                    .and("outcome", "ERROR")
            )
    }

    @Test
    fun `Assert metrics for internal error`() {
        mvc.perform(
            MockMvcRequestBuilders
                .post("/graphql")
                .content("""{ "query": "{triggerInternalFailure}" }""")
        ).andExpect(status().isOk)
            .andExpect(
                content().json(
                    """
                       |{
                       |    "errors":[
                       |       {"message":"java.lang.IllegalStateException: Exception triggered.","locations":[],
                       |           "path":["triggerInternalFailure"],"extensions":{"errorType":"INTERNAL"}}
                       |    ],
                       |    "data":{"triggerInternalFailure":null}
                       |}""".trimMargin(),
                    false
                )
            )

        val meters = qglMeters()

        assertThat(meters).containsOnlyKeys("gql.error", "gql.query", "gql.resolver")

        assertThat(meters["gql.error"]).isNotNull
            .extracting({ it?.id?.tags }, asTags)
            .containsExactlyInAnyOrderElementsOf(
                Tags.of("execution-tag", "foo")
                    .and("contextual-tag", "foo")
                    .and("gql.errorCode", "INTERNAL")
                    .and("gql.errorDetail", "none")
                    .and("gql.path", "[triggerInternalFailure]")
                    .and("outcome", "ERROR")
            )

        assertThat(meters["gql.query"]).isNotNull
            .extracting({ it?.id?.tags }, asTags)
            .containsExactlyInAnyOrderElementsOf(
                Tags.of("execution-tag", "foo")
                    .and("contextual-tag", "foo")
                    .and("outcome", "ERROR")
            )

        assertThat(meters["gql.resolver"]).isNotNull
            .extracting({ it?.id?.tags }, asTags)
            .containsExactlyInAnyOrderElementsOf(
                Tags.of("field-fetch-tag", "foo")
                    .and("contextual-tag", "foo")
                    .and("gql.field", "Query.triggerInternalFailure")
                    .and("outcome", "ERROR")
            )
    }

    @Test
    fun `Assert metrics for a DGS bad-request error`() {
        mvc.perform(
            MockMvcRequestBuilders
                .post("/graphql")
                .content("""{ "query": "{triggerBadRequestFailure}" }""")
        ).andExpect(status().isOk)
            .andExpect(
                content().json(
                    """
                        |{
                        |"errors":[
                        |   {"message":"com.netflix.graphql.dgs.exceptions.DgsBadRequestException: Exception triggered.",
                        |       "locations":[],"path":["triggerBadRequestFailure"],"extensions":{"errorType":"BAD_REQUEST"}}
                        |],
                        |"data":{"triggerBadRequestFailure":null}
                        |}""".trimMargin(),
                    false
                )
            )

        val meters = qglMeters()

        assertThat(meters).containsOnlyKeys("gql.error", "gql.query", "gql.resolver")

        assertThat(meters["gql.error"]).isNotNull
            .extracting({ it?.id?.tags }, asTags)
            .containsExactlyInAnyOrderElementsOf(
                Tags.of("execution-tag", "foo")
                    .and("contextual-tag", "foo")
                    .and("gql.errorCode", "BAD_REQUEST")
                    .and("gql.errorDetail", "none")
                    .and("gql.path", "[triggerBadRequestFailure]")
                    .and("outcome", "ERROR")
            )

        assertThat(meters["gql.query"]).isNotNull
            .extracting({ it?.id?.tags }, asTags)
            .containsExactlyInAnyOrderElementsOf(
                Tags.of("execution-tag", "foo")
                    .and("contextual-tag", "foo")
                    .and("outcome", "ERROR")
            )

        assertThat(meters["gql.resolver"]).isNotNull
            .extracting({ it?.id?.tags }, asTags)
            .containsExactlyInAnyOrderElementsOf(
                Tags.of("field-fetch-tag", "foo")
                    .and("contextual-tag", "foo")
                    .and("gql.field", "Query.triggerBadRequestFailure")
                    .and("outcome", "ERROR")
            )
    }

    @Test
    fun `Assert metrics for custom error`() {
        mvc.perform(
            MockMvcRequestBuilders
                .post("/graphql")
                .content("""{ "query": "{triggerCustomFailure}" }""")
        ).andExpect(status().isOk)
            .andExpect(
                content().json(
                    """
                    |{
                    |   "errors":[
                    |       { 
                    |           "message":"Exception triggered.","locations":[],
                    |           "path":["triggerCustomFailure"],
                    |           "extensions":{"errorType":"UNAVAILABLE","errorDetail":"ENHANCE_YOUR_CALM"}
                    |       }
                    |   ],
                    |   "data":{"triggerCustomFailure":null}
                    |}""".trimMargin(),
                    false
                )
            )

        val meters = qglMeters()

        assertThat(meters).containsOnlyKeys("gql.error", "gql.query", "gql.resolver")

        assertThat(meters["gql.error"]).isNotNull
            .extracting({ it?.id?.tags }, asTags)
            .containsExactlyInAnyOrderElementsOf(
                Tags.of("execution-tag", "foo")
                    .and("contextual-tag", "foo")
                    .and("gql.errorCode", "UNAVAILABLE")
                    .and("gql.errorDetail", "ENHANCE_YOUR_CALM")
                    .and("gql.path", "[triggerCustomFailure]")
                    .and("outcome", "ERROR")
            )

        assertThat(meters["gql.query"]).isNotNull
            .extracting({ it?.id?.tags }, asTags)
            .containsExactlyInAnyOrderElementsOf(
                Tags.of("execution-tag", "foo")
                    .and("contextual-tag", "foo")
                    .and("outcome", "ERROR")
            )

        assertThat(meters["gql.resolver"]).isNotNull
            .extracting({ it?.id?.tags }, asTags)
            .containsExactlyInAnyOrderElementsOf(
                Tags.of("field-fetch-tag", "foo")
                    .and("contextual-tag", "foo")
                    .and("gql.field", "Query.triggerCustomFailure")
                    .and("outcome", "ERROR")
            )
    }

    @Test
    fun `Assert metrics for request with multiple errors`() {
        mvc.perform(
            MockMvcRequestBuilders
                .post("/graphql")
                .content("""{ "query": "{ triggerInternalFailure triggerBadRequestFailure triggerCustomFailure }" }""")
        ).andExpect(status().isOk)
            .andExpect(
                content().json(
                    """
                    |{
                    |    "errors":[
                    |        {"message":"java.lang.IllegalStateException: Exception triggered.","locations":[],
                    |            "path":["triggerInternalFailure"],"extensions":{"errorType":"INTERNAL"}},
                    |        {"message":"com.netflix.graphql.dgs.exceptions.DgsBadRequestException: Exception triggered.","locations":[],
                    |            "path":["triggerBadRequestFailure"],"extensions":{"errorType":"BAD_REQUEST"}},
                    |        {"message":"Exception triggered.","locations":[],
                    |            "path":["triggerCustomFailure"],
                    |            "extensions":{"errorType":"UNAVAILABLE","errorDetail":"ENHANCE_YOUR_CALM"}}
                    |    ],
                    |    "data":{"triggerInternalFailure":null,"triggerBadRequestFailure":null,"triggerCustomFailure":null}
                    |}
                     """.trimMargin(),
                    false
                )
            )

        val meters = qglMetersMultiMap()

        assertThat(meters).containsOnlyKeys("gql.error", "gql.query", "gql.resolver")

        assertThat(meters["gql.query"]).hasSize(1)
        assertThat(meters["gql.error"]).hasSize(3)
        assertThat(meters["gql.resolver"]).hasSize(3)

        val errors = meters.getValue("gql.error").map { it.id.tags }
        assertThat(errors).containsExactlyInAnyOrder(
            Tags.of("execution-tag", "foo")
                .and("contextual-tag", "foo")
                .and("gql.errorCode", "BAD_REQUEST")
                .and("gql.errorDetail", "none")
                .and("gql.path", "[triggerBadRequestFailure]")
                .and("outcome", "ERROR").toList(),
            Tags.of("execution-tag", "foo")
                .and("contextual-tag", "foo")
                .and("gql.errorCode", "INTERNAL")
                .and("gql.errorDetail", "none")
                .and("gql.path", "[triggerInternalFailure]")
                .and("outcome", "ERROR").toList(),
            Tags.of("execution-tag", "foo")
                .and("contextual-tag", "foo")
                .and("gql.errorCode", "UNAVAILABLE")
                .and("gql.errorDetail", "ENHANCE_YOUR_CALM")
                .and("gql.path", "[triggerCustomFailure]")
                .and("outcome", "ERROR").toList(),
        )
    }

    private fun qglMeters(): Map<String, Meter> {
        return meterRegistry.meters
            .asSequence()
            .filter { it.id.name.startsWith("gql.") }
            .associateBy { it.id.name }
    }

    private fun qglMetersMultiMap(): Map<String, List<Meter>> {
        return meterRegistry.meters
            .asSequence()
            .filter { it.id.name.startsWith("gql.") }
            .groupBy { it.id.name }
    }

    @TestConfiguration(proxyBeanMethods = false)
    open class LocalTestConfiguration {

        @Bean
        open fun testMeterRegistry(): MeterRegistry {
            return SimpleMeterRegistry()
        }

        @Bean
        open fun contextualTagProvider(): DgsContextualTagCustomizer {
            return DgsContextualTagCustomizer { Tags.of("contextual-tag", "foo") }
        }

        @Bean
        open fun executionTagCustomizer(): DgsExecutionTagCustomizer {
            return DgsExecutionTagCustomizer { _, _, _ -> Tags.of("execution-tag", "foo") }
        }

        @Bean
        open fun fieldFetchTagCustomizer(): DgsFieldFetchTagCustomizer {
            return DgsFieldFetchTagCustomizer { _, _ -> Tags.of("field-fetch-tag", "foo") }
        }
    }

    @SpringBootApplication(
        proxyBeanMethods = false,
        scanBasePackages = ["com.netflix.graphql.dgs.metrics.micrometer.none"]
    )
    @ComponentScan(
        useDefaultFilters = false,
        includeFilters = [ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [])]
    )
    open class LocalApp {

        @DgsComponent
        class ExampleImplementation {

            @DgsTypeDefinitionRegistry
            fun typeDefinitionRegistry(): TypeDefinitionRegistry {
                val newRegistry = TypeDefinitionRegistry()

                val query =
                    ObjectTypeDefinition
                        .newObjectTypeDefinition()
                        .name("Query")
                        .fieldDefinition(
                            FieldDefinition
                                .newFieldDefinition()
                                .name("ping")
                                .type(TypeName("String"))
                                .build()
                        )
                        .fieldDefinition(
                            FieldDefinition
                                .newFieldDefinition()
                                .name("upperCased")
                                .type(TypeName("String"))
                                .build()
                        )
                        .fieldDefinition(
                            FieldDefinition
                                .newFieldDefinition()
                                .name("triggerInternalFailure")
                                .type(TypeName("String"))
                                .build()
                        )
                        .fieldDefinition(
                            FieldDefinition
                                .newFieldDefinition()
                                .name("triggerBadRequestFailure")
                                .type(TypeName("String"))
                                .build()
                        )
                        .fieldDefinition(
                            FieldDefinition
                                .newFieldDefinition()
                                .name("triggerCustomFailure")
                                .type(TypeName("String"))
                                .build()
                        )
                        .build()

                newRegistry.add(query)
                return newRegistry
            }

            @DgsData(parentType = "Query", field = "ping")
            fun ping(): String {
                return "pong"
            }

            @DgsData(parentType = "Query", field = "triggerInternalFailure")
            fun triggerInternalFailure(): String {
                throw IllegalStateException("Exception triggered.")
            }

            @DgsData(parentType = "Query", field = "triggerBadRequestFailure")
            fun triggerBadRequestFailure(): String {
                throw DgsBadRequestException("Exception triggered.")
            }

            @DgsData(parentType = "Query", field = "triggerCustomFailure")
            fun triggerCustomFailure(): String {
                throw CustomException("Exception triggered.")
            }

            @DgsData(parentType = "Query", field = "upperCased")
            fun upperCased(dfe: DataFetchingEnvironment): CompletableFuture<MutableList<String>>? {
                val dataLoader = dfe.getDataLoader<String, String>("upperCaseLoader")
                return dataLoader.loadMany(listOf("a", "b", "c"))
            }

            @DgsDataLoader(name = "upperCaseLoader")
            var batchLoader = BatchLoader { keys: List<String?>? ->
                CompletableFuture.supplyAsync { keys?.map { it?.toUpperCase() }?.toList() }
            }
        }

        @Bean
        open fun customDataFetchingExceptionHandler(): DataFetcherExceptionHandler {
            return CustomDataFetchingExceptionHandler()
        }
    }

    class CustomDataFetchingExceptionHandler : DataFetcherExceptionHandler {

        private val defaultHandler: DefaultDataFetcherExceptionHandler = DefaultDataFetcherExceptionHandler()

        override fun onException(handlerParameters: DataFetcherExceptionHandlerParameters): DataFetcherExceptionHandlerResult {
            return if (handlerParameters.exception is CustomException) {
                val exception = handlerParameters.exception
                val graphqlError: GraphQLError =
                    TypedGraphQLError
                        .ENHANCE_YOUR_CALM
                        .message(exception.message)
                        .path(handlerParameters.path)
                        .build()
                DataFetcherExceptionHandlerResult.newResult()
                    .error(graphqlError)
                    .build()
            } else {
                defaultHandler.onException(handlerParameters)
            }
        }
    }

    class CustomException(message: String?) : java.lang.IllegalStateException(message)
}
