package com.netflix.graphql.dgs.exceptions

import com.jayway.jsonpath.TypeRef
import com.jayway.jsonpath.spi.mapper.MappingException
import java.lang.RuntimeException

data class DgsQueryExecutionDataExtractionException(val ex: Exception, val jsonResult: String, val jsonPath: String, val targetClass: String) : RuntimeException(String.format("Error deserializing data from '%s' with JsonPath '%s' and target class %s", jsonResult, jsonPath, targetClass), ex) {
    constructor(ex: MappingException, jsonResult: String, jsonPath: String, targetClass: TypeRef<*>) : this(ex, jsonResult, jsonPath, targetClass.type.typeName)
    constructor(ex: MappingException, jsonResult: String, jsonPath: String, targetClass: Class<*>) : this(ex, jsonResult, jsonPath, targetClass.name)
}
