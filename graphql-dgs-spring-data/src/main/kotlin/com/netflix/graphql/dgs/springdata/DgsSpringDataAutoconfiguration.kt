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

package com.netflix.graphql.dgs.springdata

import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.ConversionService
import org.springframework.data.repository.support.DefaultRepositoryInvokerFactory
import org.springframework.data.repository.support.Repositories
import org.springframework.data.repository.support.RepositoryInvokerFactory
import org.springframework.format.support.DefaultFormattingConversionService
import java.util.*

@Configuration
@AutoConfigureAfter(JpaRepositoriesAutoConfiguration::class)
open class DgsSpringDataAutoconfiguration {

    @Bean
    open fun repositories(applicationContext: ApplicationContext): Repositories {
        return Repositories(applicationContext)
    }

    @Bean
    @ConditionalOnMissingBean
    open fun repositoryInvokerFactory(repositories: Repositories, optConversionService: Optional<ConversionService>): RepositoryInvokerFactory {
        return DefaultRepositoryInvokerFactory(repositories, optConversionService.orElse(DefaultFormattingConversionService()))
    }

    @Bean
    open fun queryAndMutationGenerator(repositories: Repositories): RepositoryDatafetcherManager {
        return RepositoryDatafetcherManager(repositories)
    }
}



