package com.netflix.graphql.dgs.autoconfig

import com.netflix.graphql.dgs.DgsQueryExecutor
import com.netflix.graphql.dgs.autoconfig.testcomponents.TestExceptionDatFetcherConfig
import com.netflix.graphql.dgs.exceptions.QueryException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.WebApplicationContextRunner

class DefaultExceptionHandlerTest {
    private val context = WebApplicationContextRunner().withConfiguration(AutoConfigurations.of(DgsAutoConfiguration::class.java))!!

    @Test
    fun queryDocumentWithDefaultException() {
        val error: QueryException = assertThrows {
            context.withUserConfiguration(TestExceptionDatFetcherConfig::class.java).run { ctx ->
                assertThat(ctx).getBean(DgsQueryExecutor::class.java).extracting {
                    it.executeAndGetDocumentContext("{errorTest}")
                }
            }
        }
        assertThat(error.errors.size).isEqualTo(1)

        assertThat(error.errors[0].extensions.get("errorType")).isEqualTo("INTERNAL")
    }
}