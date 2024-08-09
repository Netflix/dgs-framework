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

package com.netflix.graphql.dgs.exceptions

import com.jayway.jsonpath.TypeRef
import com.jayway.jsonpath.spi.mapper.MappingException

data class DgsQueryExecutionDataExtractionException(
    val ex: Exception,
    val jsonResult: String,
    val jsonPath: String,
    val targetClass: String,
) : RuntimeException(
        String.format("Error deserializing data from '%s' with JsonPath '%s' and target class %s", jsonResult, jsonPath, targetClass),
        ex,
    ) {
    constructor(
        ex: MappingException,
        jsonResult: String,
        jsonPath: String,
        targetClass: TypeRef<*>,
    ) : this(ex, jsonResult, jsonPath, targetClass.type.typeName)
    constructor(
        ex: MappingException,
        jsonResult: String,
        jsonPath: String,
        targetClass: Class<*>,
    ) : this(ex, jsonResult, jsonPath, targetClass.name)
}
