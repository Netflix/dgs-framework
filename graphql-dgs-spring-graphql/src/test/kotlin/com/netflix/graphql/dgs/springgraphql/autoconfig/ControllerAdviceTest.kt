/*
 * Copyright 2024 Netflix, Inc.
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

package com.netflix.graphql.dgs.springgraphql.autoconfig

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.DgsQueryExecutor
import com.netflix.graphql.dgs.autoconfig.DgsAutoConfiguration
import graphql.GraphQLError
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.graphql.GraphQlAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.graphql.data.method.annotation.GraphQlExceptionHandler
import org.springframework.graphql.execution.ErrorType
import org.springframework.web.bind.annotation.ControllerAdvice

@SpringBootTest(
    classes = [
        DgsSpringGraphQLAutoConfiguration::class,
        DgsAutoConfiguration::class,
        DgsSpringGraphQLSourceAutoConfiguration::class,
        GraphQlAutoConfiguration::class,
        ControllerAdviceTest.ControllerAdviceTestConfig::class,
    ],
    properties = [
        "dgs.graphql.schema-locations=classpath:/dgs-spring-graphql-smoke-test.graphqls",
        "spring.graphql.schema.inspection.enabled=true",
        "dgs.graphql.schema-wiring-validation-enabled=false",
    ],
)
class ControllerAdviceTest {
    @Autowired
    lateinit var queryExecutor: DgsQueryExecutor

    @Test
    fun testControllerAdvice() {
        @Language("GraphQL")
        val query =
            """
            query {
                withControllerAdvice
            }
            """.trimIndent()

        val result = queryExecutor.execute(query)
        assertThat(result.errors).isNotEmpty
        assertThat(result.errors.first().message).isEqualTo("Successful error handling")
        assertThat(result.errors.first().errorType).isEqualTo(ErrorType.BAD_REQUEST)
    }

    @TestConfiguration
    open class ControllerAdviceTestConfig {
        @DgsComponent
        class TestDataFetcher {
            @DgsQuery
            fun withControllerAdvice(): Unit = throw IllegalArgumentException("Testing Controller Advice")
        }

        @ControllerAdvice
        class TestControllerAdvice {
            @GraphQlExceptionHandler
            fun handle(ex: java.lang.IllegalArgumentException?): GraphQLError =
                GraphQLError
                    .newError()
                    .errorType(ErrorType.BAD_REQUEST)
                    .message("Successful error handling")
                    .build()
        }
    }
}
