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

package com.netflix.graphql.dgs.scalars

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsRuntimeWiring
import graphql.GraphQLContext
import graphql.execution.CoercedVariables
import graphql.language.Value
import graphql.schema.Coercing
import graphql.schema.CoercingParseLiteralException
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import graphql.schema.GraphQLScalarType
import graphql.schema.idl.RuntimeWiring
import org.springframework.web.multipart.MultipartFile
import java.util.Locale

@DgsComponent
class UploadScalar {
    val upload: GraphQLScalarType =
        GraphQLScalarType
            .newScalar()
            .name("Upload")
            .description("A custom scalar that represents files")
            .coercing(MultipartFileCoercing)
            .build()

    object MultipartFileCoercing : Coercing<MultipartFile, Void> {
        @Deprecated("Deprecated in Java")
        @Throws(CoercingSerializeException::class)
        override fun serialize(dataFetcherResult: Any): Void = throw CoercingSerializeException("Upload is an input-only type")

        override fun serialize(
            dataFetcherResult: Any,
            graphQLContext: GraphQLContext,
            locale: Locale,
        ): Nothing = throw CoercingSerializeException("Upload is an input-only type")

        @Deprecated("Deprecated in Java")
        @Throws(CoercingParseValueException::class)
        override fun parseValue(input: Any): MultipartFile =
            input as? MultipartFile
                ?: throw CoercingParseValueException(
                    "Expected type " +
                        MultipartFile::class.java.name +
                        " but was " +
                        input.javaClass.name,
                )

        override fun parseValue(
            input: Any,
            graphQLContext: GraphQLContext,
            locale: Locale,
        ): MultipartFile =
            input as? MultipartFile
                ?: throw CoercingParseValueException(
                    "Expected type " +
                        MultipartFile::class.java.name +
                        " but was " +
                        input.javaClass.name,
                )

        @Deprecated("Deprecated in Java")
        override fun parseLiteral(input: Any): MultipartFile =
            throw CoercingParseLiteralException(
                "Must use variables to specify Upload values",
            )

        override fun parseLiteral(
            input: Value<*>,
            variables: CoercedVariables,
            graphQLContext: GraphQLContext,
            locale: Locale,
        ): MultipartFile =
            throw CoercingParseLiteralException(
                "Must use variables to specify Upload values",
            )
    }

    // add the scalar manually since we can't use @DgsScalar in the framework
    @DgsRuntimeWiring
    fun addScalar(builder: RuntimeWiring.Builder): RuntimeWiring.Builder = builder.scalar(upload)
}
