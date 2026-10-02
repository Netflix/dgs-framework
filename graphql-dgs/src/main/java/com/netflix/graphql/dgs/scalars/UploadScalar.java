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

package com.netflix.graphql.dgs.scalars;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsRuntimeWiring;
import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.language.Value;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;
import graphql.schema.idl.RuntimeWiring;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;

@DgsComponent
public class UploadScalar {
    private final GraphQLScalarType upload = GraphQLScalarType
            .newScalar()
            .name("Upload")
            .description("A custom scalar that represents files")
            .coercing(MultipartFileCoercing.INSTANCE)
            .build();

    @NotNull
    public GraphQLScalarType getUpload() {
        return upload;
    }

    public static final class MultipartFileCoercing implements Coercing<MultipartFile, Void> {
        @NotNull
        public static final MultipartFileCoercing INSTANCE = new MultipartFileCoercing();

        private MultipartFileCoercing() {
        }

        @NotNull
        @Override
        @Deprecated
        public Void serialize(@NotNull Object dataFetcherResult) throws CoercingSerializeException {
            throw new CoercingSerializeException("Upload is an input-only type");
        }

        @NotNull
        @Override
        public Void serialize(@NotNull Object dataFetcherResult, @NotNull GraphQLContext graphQLContext, @NotNull Locale locale) {
            throw new CoercingSerializeException("Upload is an input-only type");
        }

        @NotNull
        @Override
        @Deprecated
        public MultipartFile parseValue(@NotNull Object input) throws CoercingParseValueException {
            return asMultipartFile(input);
        }

        @NotNull
        @Override
        public MultipartFile parseValue(@NotNull Object input, @NotNull GraphQLContext graphQLContext, @NotNull Locale locale) {
            return asMultipartFile(input);
        }

        @NotNull
        @Override
        @Deprecated
        public MultipartFile parseLiteral(@NotNull Object input) {
            throw new CoercingParseLiteralException("Must use variables to specify Upload values");
        }

        @NotNull
        @Override
        public MultipartFile parseLiteral(
                @NotNull Value<?> input, @NotNull CoercedVariables variables, @NotNull GraphQLContext graphQLContext, @NotNull Locale locale) {
            throw new CoercingParseLiteralException("Must use variables to specify Upload values");
        }

        private static MultipartFile asMultipartFile(Object input) {
            if (input instanceof MultipartFile multipartFile) {
                return multipartFile;
            }
            throw new CoercingParseValueException(
                    "Expected type " + MultipartFile.class.getName() + " but was " + input.getClass().getName());
        }
    }

    // add the scalar manually since we can't use @DgsScalar in the framework
    @NotNull
    @DgsRuntimeWiring
    public RuntimeWiring.Builder addScalar(@NotNull RuntimeWiring.Builder builder) {
        return builder.scalar(upload);
    }
}
