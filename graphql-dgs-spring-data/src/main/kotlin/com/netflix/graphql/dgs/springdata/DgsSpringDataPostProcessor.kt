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

import com.netflix.graphql.dgs.bean.registry.DgsDataBeanDefinitionRegistryUtils.streamsOfBeansOfKind
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.config.BeanDefinitionHolder
import org.springframework.beans.factory.support.BeanDefinitionBuilder
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils
import org.springframework.data.repository.core.support.RepositoryFactoryInformation
import java.util.stream.Collectors


class DgsSpringDataPostProcessor : BeanDefinitionRegistryPostProcessor {

    override fun postProcessBeanDefinitionRegistry(registry: BeanDefinitionRegistry) {
        val candidates = streamsOfBeansOfKind(registry, RepositoryFactoryInformation::class.java)
                .map { SpringDataRepositoryBeanDefinition(it.beanClass, it) }.collect(Collectors.toList())
        debugGraphqlRepositories(candidates)
        registerBean(registry, candidates)
    }

    private fun debugGraphqlRepositories(graphqlRepositories: List<SpringDataRepositoryBeanDefinition>) {
        logger.info("""
        |===== DGS GraphQL Repositories =====
        |------ TODO ADD ASCII ART     ------
        |${graphqlRepositories.joinToString(prefix = "<<\n", postfix = "\n>>", separator = ",\n")}
        |====================================
        |""".trimMargin())

    }

    private fun registerBean(registry: BeanDefinitionRegistry,
                             graphqlRepositories: List<SpringDataRepositoryBeanDefinition>) {
        val beanDefinition = BeanDefinitionBuilder
                .genericBeanDefinition(RepositoryDatafetcherManager::class.java)
                .setScope(BeanDefinition.SCOPE_SINGLETON)
                .addConstructorArgValue(graphqlRepositories)
                .beanDefinition

        val beanDefHolder = BeanDefinitionHolder(beanDefinition, "dgsGraphqlRepositoryDataFetcherManager")
        logger.debug("Attempting to register {} based on {}", beanDefHolder, beanDefinition)
        BeanDefinitionReaderUtils.registerBeanDefinition(beanDefHolder, registry)
        logger.info("Bean {} registered based on {}.", beanDefHolder, beanDefinition)
    }

    override fun postProcessBeanFactory(beanFactory: ConfigurableListableBeanFactory) {
        //no-op
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DgsSpringDataPostProcessor::class.java)
    }

}

