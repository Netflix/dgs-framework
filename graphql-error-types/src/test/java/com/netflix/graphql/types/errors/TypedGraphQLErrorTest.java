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

import graphql.execution.ResultPath;
import graphql.language.SourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

public class TypedGraphQLErrorTest {
    @Test
    void equalsTest() {
        TypedGraphQLError baseError = TypedGraphQLError.newPermissionDeniedBuilder()
                .message("org.springframework.security.access.AccessDeniedException: Access is denied")
                .path(ResultPath.parse("/graphqlEndpoint"))
                .build();

        TypedGraphQLError sameError = TypedGraphQLError.newPermissionDeniedBuilder()
                .message("org.springframework.security.access.AccessDeniedException: Access is denied")
                .path(ResultPath.parse("/graphqlEndpoint"))
                .build();

        Assertions.assertEquals(baseError, sameError);
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

        Assertions.assertNotEquals(baseError, differentMessageError);

        TypedGraphQLError differentLocationsError = TypedGraphQLError.newPermissionDeniedBuilder()
                .message("Different Error Message")
                .location(new SourceLocation(0, 0))
                .path(ResultPath.parse("/graphqlEndpoint"))
                .build();

        Assertions.assertNotEquals(baseError, differentLocationsError);

        TypedGraphQLError differentPathError = TypedGraphQLError.newPermissionDeniedBuilder()
                .message("org.springframework.security.access.AccessDeniedException: Access is denied")
                .path(ResultPath.parse("/differentGraphQlEndpoint"))
                .build();

        Assertions.assertNotEquals(baseError, differentPathError);

        TypedGraphQLError differentExtensionsError = TypedGraphQLError.newPermissionDeniedBuilder()
                .message("org.springframework.security.access.AccessDeniedException: Access is denied")
                .path(ResultPath.parse("/differentGraphQlEndpoint"))
                .extensions(new HashMap<String, Object>() {{
                    put("key", "value");
                }})
                .build();

        Assertions.assertNotEquals(baseError, differentExtensionsError);
    }
}
