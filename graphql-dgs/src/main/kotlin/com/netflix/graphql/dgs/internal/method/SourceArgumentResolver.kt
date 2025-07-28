/*
 * Copyright 2025 Netflix, Inc.
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

package com.netflix.graphql.dgs.internal.method

import com.netflix.graphql.dgs.Source
import graphql.schema.DataFetchingEnvironment
import org.springframework.core.MethodParameter

class SourceArgumentResolver : ArgumentResolver {
    override fun supportsParameter(parameter: MethodParameter): Boolean = parameter.hasParameterAnnotation(Source::class.java)

    override fun resolveArgument(
        parameter: MethodParameter,
        dfe: DataFetchingEnvironment,
    ): Any {
        val source = dfe.getSource<Any>()
        if (source == null) {
            throw IllegalArgumentException("Source is null. Are you trying to use @Source on a root field (e.g. @DgsQuery)?")
        }

        if (parameter.parameterType.isAssignableFrom(source.javaClass)) {
            return source
        } else {
            throw IllegalArgumentException(
                "Invalid source type '${source?.javaClass?.name}'. Expected type '${parameter.parameterType.name}'",
            )
        }
    }
}
