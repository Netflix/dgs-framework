/*
 * Copyright 2020 Netflix, Inc.
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
import kotlin.collections.LinkedHashMap

abstract class BaseProjectionNode(
    /**
     * Explicit GraphQL Schema type. This, for example, is used to define the GraphQL Type when resolving a _fragment_.
     * For example, let's say the `schemaType` is `"Movie"`, then  the _Entity fragment_ associated to
     * this node will be:
     *
     * ```
     *  ... on Movie {
     * ```
     */
    val schemaType: Optional<String> = Optional.empty()
) {

    val fields: MutableMap<String, Any?> = LinkedHashMap()
    val fragments: MutableList<BaseSubProjectionNode<*, *>> = LinkedList()
    val inputArguments: MutableMap<String, List<InputArgument>> = LinkedHashMap()

    data class InputArgument(val name: String, val value: Any)
}
