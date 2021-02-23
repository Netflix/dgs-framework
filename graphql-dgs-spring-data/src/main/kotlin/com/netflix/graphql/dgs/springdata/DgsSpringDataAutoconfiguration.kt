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

import com.netflix.graphql.dgs.springdata.DgsSpringDataPostProcessor
import org.springframework.beans.factory.BeanClassLoaderAware
import org.springframework.beans.factory.support.BeanDefinitionBuilder
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.ConversionService
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.mapping.context.PersistentEntities
import org.springframework.data.repository.config.RepositoryBeanDefinitionRegistrarSupport
import org.springframework.data.repository.config.RepositoryConfigurationExtension
import org.springframework.data.repository.config.RepositoryConfigurationExtensionSupport
import org.springframework.data.repository.config.RepositoryConfigurationSource
import org.springframework.data.repository.core.support.RepositoryFactoryBeanSupport
import org.springframework.data.repository.support.DefaultRepositoryInvokerFactory
import org.springframework.data.repository.support.Repositories
import org.springframework.data.repository.support.RepositoryInvoker
import org.springframework.data.repository.support.RepositoryInvokerFactory
import org.springframework.format.support.DefaultFormattingConversionService
import org.springframework.util.Assert
import org.springframework.util.ClassUtils
import org.springframework.util.MultiValueMap
import org.springframework.util.StringUtils
import java.lang.reflect.Method
import java.util.*
import java.util.function.Function
import java.util.function.Supplier
import org.springframework.data.util.Lazy as DataLazy

@Configuration
@AutoConfigureAfter(JpaRepositoriesAutoConfiguration::class)
open class DgsSpringDataAutoconfiguration {



    @ConditionalOnMissingBean
    @Bean
    open fun repositories(applicationContext: ApplicationContext): Repositories {
        return Repositories(applicationContext)
    }

    @ConditionalOnMissingBean
    @Bean
    open fun repositoryInvokerFactory(repositories: Repositories, conversionService: Optional<ConversionService>): RepositoryInvokerFactory {
        return DefaultRepositoryInvokerFactory(repositories, conversionService.orElse(DefaultFormattingConversionService()))
    }


    @Bean
    open fun dgsSpringDataPostProcessor():DgsSpringDataPostProcessor  {
        return DgsSpringDataPostProcessor()
    }
}



