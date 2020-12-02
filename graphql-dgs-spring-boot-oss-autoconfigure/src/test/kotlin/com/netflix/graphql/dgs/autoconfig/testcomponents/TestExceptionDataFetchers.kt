package com.netflix.graphql.dgs.autoconfig.testcomponents

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsData
import graphql.schema.DataFetchingEnvironment
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/*
    Config class for the security data fetcher test
     */
@Configuration
open class TestExceptionDatFetcherConfig {
    @Bean
    open fun createDgsComponent(): TestExceptionDataFetcher {
        return TestExceptionDataFetcher()
    }
}

@Suppress("UNUSED_PARAMETER")
@DgsComponent
class TestExceptionDataFetcher {

    @DgsData(parentType = "Query", field = "errorTest")
    fun errorTest(dfe: DataFetchingEnvironment): String {
        throw RuntimeException()
    }
}