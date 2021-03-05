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

import com.fasterxml.jackson.databind.ObjectMapper
import java.util.*

class EntitiesGraphQLQuery : GraphQLQuery {
    val variables: MutableMap<String, Any> = LinkedHashMap()

    constructor(representations: List<Any>?) {
        variables["representations"] = representations!!
    }

    constructor()

    override fun getOperationType(): String {
        return "query(\$representations: [_Any!]!)"
    }

    override fun getOperationName(): String {
        return "_entities(representations: \$representations)"
    }

    class Builder {
        private val representations: MutableList<Any> = ArrayList()
        val mapper = ObjectMapper()

        fun build(): EntitiesGraphQLQuery {
            return EntitiesGraphQLQuery(representations)
        }

        fun addRepresentationAsVariable(representation: Any): Builder {
            representations.add(mapper.convertValue(representation, HashMap::class.java))
            return this
        }
    }

    companion object {
        fun newRequest(): Builder {
            return Builder()
        }
    }
}
