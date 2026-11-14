/*
 * Copyright 2026 Netflix, Inc.
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

package com.netflix.graphql.dgs.internal

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsComponentSource
import org.springframework.beans.factory.getBeansWithAnnotation
import org.springframework.context.ApplicationContext

/**
 * Finds the DGS components of the primary context and of each [DgsComponentSource], and the contexts they live in.
 * The primary context is read first, then the sources in order; a component found more than once is kept once.
 */
internal class DgsComponentDiscovery(
    private val applicationContext: ApplicationContext,
    private val componentSources: List<DgsComponentSource>,
    private val componentFilter: ((Any) -> Boolean)?,
) {
    fun components(): List<DgsBean> {
        val own =
            applicationContext
                .getBeansWithAnnotation<DgsComponent>()
                .values
                .asSequence()
                .map { bean -> bean to applicationContext }
        val fromSources =
            componentSources.asSequence().flatMap { source ->
                source.components().asSequence().map { bean -> bean to source.applicationContext }
            }
        return (own + fromSources)
            .distinctBy { (bean, _) -> bean }
            .filter { (bean, _) -> componentFilter?.invoke(bean) ?: true }
            .map { (bean, context) -> DgsBean(bean, context) }
            .toList()
    }

    fun contexts(): List<ApplicationContext> = (listOf(applicationContext) + componentSources.map { it.applicationContext }).distinct()
}
