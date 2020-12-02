package com.netflix.graphql.dgs.webmvc.autoconfigure

import com.netflix.graphql.dgs.DgsQueryExecutor
import com.netflix.graphql.dgs.internal.DgsSchemaProvider
import com.netflix.graphql.dgs.mvc.DgsRestController
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnWebApplication
open class DgsWebMvcAutoconfiguration {
    @Bean
    open fun dgsRestController(dgsSchemaProvider: DgsSchemaProvider, dgsQueryExecutor: DgsQueryExecutor): DgsRestController {
        return DgsRestController(dgsSchemaProvider, dgsQueryExecutor)
    }
}