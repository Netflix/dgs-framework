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

package com.netflix.graphql.dgs.springdata.autoconfiguration

import com.netflix.graphql.dgs.springdata.GraphqlRepositoryBeanDefinitionType
import com.netflix.graphql.dgs.springdata.DgsSpringDataPostProcessor
import com.netflix.graphql.dgs.springdata.RepositoryDatafetcherManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


@Configuration
open class DgsSpringDataAutoconfiguration {

    @Bean
    open fun dgsSpringDataPostProcessor():DgsSpringDataPostProcessor  {
        return DgsSpringDataPostProcessor()
    }

    @Bean
    open fun repositoryDatafetcherManager(repositoryBeans: List<GraphqlRepositoryBeanDefinitionType>): RepositoryDatafetcherManager {
        return RepositoryDatafetcherManager(repositoryBeans)
    }
}