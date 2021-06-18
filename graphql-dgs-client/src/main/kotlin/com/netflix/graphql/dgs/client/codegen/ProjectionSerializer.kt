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

package com.netflix.graphql.dgs.client.codegen

import java.util.*

class ProjectionSerializer(private val inputValueSerializer: InputValueSerializer) {
    fun serialize(projection: BaseProjectionNode): String {
        if (projection.fields.isEmpty() && projection.fragments.isEmpty()) {
            return ""
        }

        val joiner = StringJoiner(" ", "{ ", " }")
        projection.fields.forEach { (key, value) ->
            val field = if (projection.inputArguments[key] != null) {
                val inputArgsJoiner = StringJoiner(", ", "(", ")")
                projection.inputArguments[key]?.forEach {
                    inputArgsJoiner.add("${it.name}: ${inputValueSerializer.serialize(it.value)}")
                }

                key + inputArgsJoiner.toString()
            } else {
                key
            }

            joiner.add(field)
            if (value != null) {
                if (value is BaseProjectionNode) {
                    joiner.add(" ").add(serialize(value))
                } else {
                    joiner.add(" ").add(value.toString())
                }
            }
        }

        projection.fragments.forEach { joiner.add(serialize(it)) }

        return joiner.toString()
    }
}
