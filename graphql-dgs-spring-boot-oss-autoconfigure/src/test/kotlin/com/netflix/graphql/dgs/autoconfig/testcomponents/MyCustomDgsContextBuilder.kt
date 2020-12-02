package com.netflix.graphql.dgs.autoconfig.testcomponents

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsData
import com.netflix.graphql.dgs.context.DgsContext
import com.netflix.graphql.dgs.context.DgsCustomContextBuilder
import graphql.schema.DataFetchingEnvironment
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
open class CustomContextBuilderConfig {
    @Bean
    open fun createCustomContextBuilder(): DgsCustomContextBuilder<*> {
        return MyCustomDgsContextBuilder()
    }

    @Bean
    open fun createDataFetcher(): CustomContextDataFetcher {
        return CustomContextDataFetcher()
    }

}

class MyCustomDgsContextBuilder: DgsCustomContextBuilder<MyCustomContext> {
    override fun build(): MyCustomContext {
        return MyCustomContext("Hello custom context")
    }
}

class MyCustomContext(val message: String)

@DgsComponent
class CustomContextDataFetcher {
    @DgsData(parentType = "Query", field = "hello")
    fun hello(dfe: DataFetchingEnvironment): String {
        val customContext = DgsContext.getCustomContext<MyCustomContext>(dfe)
        return customContext.message
    }
}
