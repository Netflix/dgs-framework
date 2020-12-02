package com.netflix.graphql.dgs.autoconfig.testcomponents

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsData
import graphql.schema.DataFetchingEnvironment
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/*
    Config class for the Hello data fetcher test
     */
@Configuration
open class HelloDatFetcherConfig {
    @Bean
    open fun createDgsComponent(): HelloDataFetcher {
        return HelloDataFetcher()
    }
}

@Suppress("UNUSED_PARAMETER")
@DgsComponent
class HelloDataFetcher {
    @DgsData(parentType = "Query", field = "hello")
    fun hello(dfe: DataFetchingEnvironment): String {
        if(dfe.arguments["name"] != null) {
            return "Hello, ${dfe.arguments["name"]}!"
        }

        return "Hello!"
    }

    @DgsData(parentType = "Query", field = "withNullableNull")
    fun withNullableNull(): String? {
        return null
    }

    @DgsData(parentType = "Query", field = "withNonNullableNull")
    fun withNonNullableNull(): String? {
        return null
    }
}