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

package com.netflix.graphql.dgs.internal.kotlin.test

import com.netflix.graphql.dgs.internal.java.test.inputobjects.JFilterEntry
import com.netflix.graphql.dgs.internal.java.test.inputobjects.JFilterField
import com.netflix.graphql.dgs.internal.java.test.inputobjects.JFilterOperator

enum class KGreetingType {
    FRIENDLY,
    POLITE
}

data class KInputMessage(val type: KGreetingType, val typeList: List<KGreetingType>)

data class KMovieFilter(val movieIds: List<Any>)

data class KFooInput(val bars: List<KBarInput>)

data class KBarInput(val name: String, val value: Any)

data class KFilter(val query: Any)

class KConcreteFilterEntry(
    operator: JFilterOperator?,
    values: List<String?>?,
    val field: JFilterField?
) : JFilterEntry(operator, values)
