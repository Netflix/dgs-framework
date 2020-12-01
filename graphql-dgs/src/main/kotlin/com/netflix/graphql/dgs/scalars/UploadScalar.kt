package com.netflix.graphql.dgs.scalars

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsRuntimeWiring
import graphql.schema.*
import graphql.schema.idl.RuntimeWiring
import org.springframework.web.multipart.MultipartFile


@DgsComponent
class UploadScalar {
    val upload:GraphQLScalarType = GraphQLScalarType.newScalar().name("Upload")
            .description("A custom scalar that represents files")
            .coercing(MultipartFileCoercing).build()

    object MultipartFileCoercing : Coercing<MultipartFile, Void> {
        @Throws(CoercingSerializeException::class)
        override fun serialize(dataFetcherResult: Any): Void {
            throw CoercingSerializeException("Upload is an input-only type")
        }

        @Throws(CoercingParseValueException::class)
        override fun parseValue(input: Any): MultipartFile? {
            return if (input is MultipartFile) {
                input
            } else run {
                throw CoercingParseValueException("Expected type " +
                        MultipartFile::class.java.name +
                        " but was " +
                        input.javaClass.name)
            }
        }

        override fun parseLiteral(input: Any): MultipartFile {
            throw CoercingParseLiteralException(
                    "Must use variables to specify Upload values")
        }
    }

    // add the scalar manually since we can't use @DgsScalar in the framework
    @DgsRuntimeWiring
    fun addScalar(builder: RuntimeWiring.Builder): RuntimeWiring.Builder {
        return builder.scalar(upload)
    }
}

