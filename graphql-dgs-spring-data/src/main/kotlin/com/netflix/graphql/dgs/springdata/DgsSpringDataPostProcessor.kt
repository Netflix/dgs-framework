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

import com.netflix.graphql.dgs.bean.registry.AnnotatedBeanClassReference
import com.netflix.graphql.dgs.bean.registry.DgsDataBeanDefinitionRegistryUtils
import com.netflix.graphql.dgs.springdata.annotations.DgsSpringDataConfiguration
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor
import org.springframework.data.repository.Repository
import org.springframework.data.repository.core.RepositoryMetadata
import org.springframework.data.repository.core.support.AbstractRepositoryMetadata
import java.util.*
import java.util.stream.Stream


class DgsSpringDataPostProcessor : BeanDefinitionRegistryPostProcessor {

    override fun postProcessBeanDefinitionRegistry(registry: BeanDefinitionRegistry) {
        val streamOfDgsAnnotatedBeans = DgsDataBeanDefinitionRegistryUtils.streamsOfAnnotatedClassReferences(registry, DgsSpringDataConfiguration::class.java)
        val streamOfGraphlRepositories = toStreamOfGraphqlRepositories(streamOfDgsAnnotatedBeans)
        // Call other stuff that will build more intresting things.
    }


    override fun postProcessBeanFactory(beanFactory: ConfigurableListableBeanFactory) {
        //no-op
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DgsSpringDataPostProcessor::class.java)

        internal fun toStreamOfGraphqlRepositories(stream: Stream<AnnotatedBeanClassReference<DgsSpringDataConfiguration>>): Stream<GraphqlRepositoryBeanDefinitionType> {
            return stream.map { annotatedBeanClassRef ->
                val beanClass = annotatedBeanClassRef.beanClass
                if (Repository::class.java.isAssignableFrom(beanClass)) {
                    val repositoryMetadata = AbstractRepositoryMetadata.getMetadata(beanClass)
                    if (repositoryMetadata == null) {
                        logger.info("DGS SpringDataConfiguration bean found but no RepositoryMetadata is available for {}.", annotatedBeanClassRef)
                        Optional.empty()
                    } else {
                        logger.info("DGS SpringDataConfiguration bean found{}, RepositoryMetadata available {}.", annotatedBeanClassRef, repositoryMetadata)
                        Optional.of(GraphqlRepositoryBeanDefinitionType(annotatedBeanClassRef, repositoryMetadata))
                    }
                } else {
                    logger.warn("DGS SpringDataConfiguration bean found {} but is not a Spring Data Repository!.", annotatedBeanClassRef)
                    Optional.empty()
                }
            }.filter { it.isPresent }.map { it.get() }

        }

    }

}

data class GraphqlRepositoryBeanDefinitionType(
        val beanDefinition: AnnotatedBeanClassReference<DgsSpringDataConfiguration>,
        val repositoryMetadata: RepositoryMetadata
)