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

abstract class BaseSubProjectionNode<T, R>(
    val parent: T,
    val root: R,
    schemaType: Optional<String> = Optional.empty()
) : BaseProjectionNode(schemaType) {

    constructor(parent: T, root: R) : this(parent, root, schemaType = Optional.empty())

    fun parent(): T {
        return parent
    }

    fun root(): R {
        return root
    }
}
