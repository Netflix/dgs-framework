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

package com.netflix.graphql.types.errors;

import graphql.ErrorClassification;
import graphql.GraphQLError;
import graphql.execution.ResultPath;
import graphql.language.SourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TypedGraphQLErrorTest {
    @Test
    void equalsTest() {
        TypedGraphQLError baseError = TypedGraphQLError.newPermissionDeniedBuilder()
                .message("org.springframework.security.access.AccessDeniedException: Access is denied")
                .path(ResultPath.parse("/graphqlEndpoint"))
                .location(new SourceLocation(5, 10))
                .extensions(Map.of("foo", "bar"))
                .build();

        TypedGraphQLError sameError = TypedGraphQLError.newPermissionDeniedBuilder()
                .message("org.springframework.security.access.AccessDeniedException: Access is denied")
                .path(ResultPath.parse("/graphqlEndpoint"))
                .location(new SourceLocation(5, 10))
                .extensions(Map.of("foo", "bar"))
                .build();

        assertEquals(baseError, sameError);
    }

    @Test
    void hashCodeTest() {
        TypedGraphQLError baseError = TypedGraphQLError.newPermissionDeniedBuilder()
                .message("org.springframework.security.access.AccessDeniedException: Access is denied")
                .path(ResultPath.parse("/graphqlEndpoint"))
                .location(SourceLocation.EMPTY)
                .extensions(Map.of("foo", "bar"))
                .build();

        TypedGraphQLError sameError = TypedGraphQLError.newPermissionDeniedBuilder()
                .message("org.springframework.security.access.AccessDeniedException: Access is denied")
                .path(ResultPath.parse("/graphqlEndpoint"))
                .location(SourceLocation.EMPTY)
                .extensions(Map.of("foo", "bar"))
                .build();

        assertEquals(baseError.hashCode(), sameError.hashCode());
    }

    @Test
    void notEqualsTest() {
        TypedGraphQLError baseError = TypedGraphQLError.newPermissionDeniedBuilder()
                .message("org.springframework.security.access.AccessDeniedException: Access is denied")
                .path(ResultPath.parse("/graphqlEndpoint"))
                .build();

        TypedGraphQLError differentMessageError = TypedGraphQLError.newPermissionDeniedBuilder()
                .message("Different Error Message")
                .path(ResultPath.parse("/graphqlEndpoint"))
                .build();

        assertNotEquals(baseError, differentMessageError);

        TypedGraphQLError differentLocationsError = TypedGraphQLError.newPermissionDeniedBuilder()
                .message("Different Error Message")
                .location(new SourceLocation(0, 0))
                .path(ResultPath.parse("/graphqlEndpoint"))
                .build();

        assertNotEquals(baseError, differentLocationsError);

        TypedGraphQLError differentPathError = TypedGraphQLError.newPermissionDeniedBuilder()
                .message("org.springframework.security.access.AccessDeniedException: Access is denied")
                .path(ResultPath.parse("/differentGraphQlEndpoint"))
                .build();

        assertNotEquals(baseError, differentPathError);

        TypedGraphQLError differentExtensionsError = TypedGraphQLError.newPermissionDeniedBuilder()
                .message("org.springframework.security.access.AccessDeniedException: Access is denied")
                .path(ResultPath.parse("/differentGraphQlEndpoint"))
                .extensions(Map.of("key", "value"))
                .build();

        assertNotEquals(baseError, differentExtensionsError);
    }

    @Test
    void specificationTest() {
        GraphQLError error = TypedGraphQLError.newInternalErrorBuilder()
                .message("unknown error")
                .build();
        Map<String, Object> spec = error.toSpecification();
        assertEquals("unknown error", spec.get("message"));
        assertNull(spec.get("locations"));
        Map<?, ?> extensions = (Map<?, ?>) spec.get("extensions");
        assertNotNull(extensions);
        assertEquals("INTERNAL", extensions.get("errorType"));
    }

    @Test
    void customExtensionsAndClassificationSpecificationTest() {
        GraphQLError error = TypedGraphQLError.newBuilder()
                .message("unknown error")
                .path(ResultPath.rootPath())
                .errorType(ErrorClassification.errorClassification("unknown"))
                .location(new SourceLocation(1, 10))
                .extensions(Map.of("foo", "bar"))
                .build();
        Map<String, Object> spec = error.toSpecification();
        assertEquals("unknown error", spec.get("message"));
        List<?> locations = (List<?>) spec.get("locations");
        assertNotNull(locations);
        assertEquals(1, locations.size());
        assertEquals(Map.of("line", 1, "column", 10), locations.get(0));

        Map<?, ?> extensions = (Map<?, ?>) spec.get("extensions");
        assertNotNull(extensions);
        assertEquals("bar", extensions.get("foo"));
        assertEquals("unknown", extensions.get("classification"));
    }

    @ParameterizedTest
    @MethodSource("reflexiveEqualitySource")
    void reflexiveEqualityWithNullFieldsTest(TypedGraphQLError error, TypedGraphQLError sameError) {
        assertEquals(error, sameError);
    }

    private static Stream<Arguments> reflexiveEqualitySource() {
        return Stream.of(
                Arguments.of(
                        TypedGraphQLError.newBuilder().build(),
                        TypedGraphQLError.newBuilder().build()
                ),
                Arguments.of(
                        TypedGraphQLError.newBadRequestBuilder().build(),
                        TypedGraphQLError.newBadRequestBuilder().build()
                ),
                Arguments.of(
                        errorWithoutMessage(),
                        errorWithoutMessage()
                ),
                Arguments.of(
                        errorWithoutLocation(),
                        errorWithoutLocation()
                ),
                Arguments.of(
                        errorWithoutPath(),
                        errorWithoutPath()
                )
        );
    }

    private static TypedGraphQLError errorWithoutMessage() {
        return TypedGraphQLError.newBuilder()
                .location(new SourceLocation(0, 0))
                .path(ResultPath.parse("/someGraphQlEndpoint"))
                .build();
    }

    private static TypedGraphQLError errorWithoutLocation() {
        return TypedGraphQLError.newBuilder()
                .message("Some error message")
                .path(ResultPath.parse("/someGraphQlEndpoint"))
                .build();
    }

    private static TypedGraphQLError errorWithoutPath() {
        return TypedGraphQLError.newBuilder()
                .message("Some error message")
                .location(new SourceLocation(0, 0))
                .build();
    }
}
