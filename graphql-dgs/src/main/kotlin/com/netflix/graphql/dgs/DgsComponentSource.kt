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

package com.netflix.graphql.dgs

import org.springframework.beans.factory.getBeansWithAnnotation
import org.springframework.context.ApplicationContext

/**
 * **Netflix Internal API**
 *
 * Contributes DGS components from an [ApplicationContext] other than the one DGS is configured with,
 * allowing a single schema be built from several Spring contexts.
 *
 * DGS reads the components of its own context first and then those of each source. A component returned
 * by a source is processed against that source's [applicationContext]: its `@DgsScalar` and `@DgsDirective`
 * beans are registered, and the context is exposed through [DgsDataFetchingEnvironment].
 *
 * The default [components] returns the `@DgsComponent` beans of [applicationContext]. Implementations may
 * override it to select components themselves.
 */
@Internal
interface DgsComponentSource {
    val applicationContext: ApplicationContext

    fun components(): Collection<Any> = applicationContext.getBeansWithAnnotation<DgsComponent>().values
}
