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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import graphql.ErrorClassification;
import graphql.GraphQLError;
import graphql.GraphqlErrorHelper;
import graphql.execution.ResultPath;
import graphql.language.SourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static graphql.Assert.assertNotNull;

public class TypedGraphQLError implements GraphQLError {

    private final String message;
    @Nullable
    private final List<SourceLocation> locations;
    @Nullable
    private final ErrorClassification classification;
    @Nullable
    private final List<Object> path;
    @Nullable
    private final Map<String, Object> extensions;

    @JsonCreator
    public TypedGraphQLError(@JsonProperty("message") String message,
                             @JsonProperty("locations") List<SourceLocation> locations,
                             @JsonProperty("classification") ErrorClassification classification,
                             @JsonProperty("path") List<Object> path,
                             @JsonProperty("extensions") Map<String, Object> extensions) {
        this.message = message;
        this.locations = locations;
        this.classification = classification;
        this.path = path;
        this.extensions = extensions;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public List<SourceLocation> getLocations() {
        return locations;
    }

    @Override
    public ErrorClassification getErrorType() {
        return classification;
    }

    @Override
    public List<Object> getPath() {
        return path;
    }

    @Override
    public Map<String, Object> getExtensions() {
        return extensions;
    }

    @Override
    public Map<String, Object> toSpecification() {
        // We override toSpecification and explicitly don't delegate
        // to GraphqlErrorHelper.toSpecification because it will add
        // classification to extensions, but our builder has already
        // handled that.

        Map<String, Object> errorMap = newLinkedHashMap(4);
        errorMap.put("message", message);
        if (locations != null) {
            errorMap.put("locations", GraphqlErrorHelper.locations(locations));
        }
        if (path != null) {
            errorMap.put("path", path);
        }
        if (extensions != null) {
            errorMap.put("extensions", extensions);
        }
        return errorMap;
    }

    /**
     * Create new Builder instance to customize error.
     *
     * @return A new TypedGraphQLError.Builder instance to further customize the error.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Create new Builder instance to customize error.
     * @return A new TypedGraphQLError.Builder instance to further customize the error. Pre-sets ErrorType.INTERNAL.
     */
    public static Builder newInternalErrorBuilder() {
        return newBuilder().errorType(ErrorType.INTERNAL);
    }

    /**
     * Create new Builder instance to customize error.
     * @return A new TypedGraphQLError.Builder instance to further customize the error. Pre-sets ErrorType.NOT_FOUND.
     */
    public static Builder newNotFoundBuilder() {
        return newBuilder().errorType(ErrorType.NOT_FOUND);
    }

    /**
     * Create new Builder instance to customize error.
     * @return A new TypedGraphQLError.Builder instance to further customize the error. Pre-sets ErrorType.PERMISSION_DENIED.
     */
    public static Builder newPermissionDeniedBuilder() {
        return newBuilder().errorType(ErrorType.PERMISSION_DENIED);
    }

    /**
     * Create new Builder instance to customize error.
     * @return A new TypedGraphQLError.Builder instance to further customize the error. Pre-sets ErrorType.BAD_REQUEST.
     */
    public static Builder newBadRequestBuilder() {
        return newBuilder().errorType(ErrorType.BAD_REQUEST);
    }

    /**
     * Create new Builder instance to further customize an error that results in a {@link ErrorDetail.Common#CONFLICT conflict}.
     * @return A new TypedGraphQLError.Builder instance to further customize the error. Pre-sets {@link ErrorDetail.Common#CONFLICT}.
     */
    public static Builder newConflictBuilder() {
        return newBuilder().errorDetail(ErrorDetail.Common.CONFLICT);
    }

    @Override
    public String toString() {
        return "TypedGraphQLError{" +
                "message='" + message + '\'' +
                ", locations=" + locations +
                ", classification=" + classification +
                ", path=" + path +
                ", extensions=" + extensions +
                '}';
    }

    @Override
    public int hashCode() {
        return GraphqlErrorHelper.hashCode(this);
    }

    @Override
    public boolean equals(Object obj) {
        return GraphqlErrorHelper.equals(this, obj);
    }

    public static final class Builder implements GraphQLError.Builder<Builder> {
        private String message;
        private List<Object> path;
        private List<SourceLocation> locations;
        private ErrorClassification errorClassification = ErrorType.UNKNOWN;
        private Map<String, Object> extensions;
        private String origin;
        private String debugUri;
        private Map<String, Object> debugInfo;

        private Builder() {
        }

        private String defaultMessage() {
            return errorClassification.toString();
        }

        private Map<String, Object> getExtensions() {
            final Map<String, Object> extensionsMap;
            if (extensions == null) {
                extensionsMap = newLinkedHashMap(5);
            } else {
                extensionsMap = newLinkedHashMap(extensions.size() + 5);
                extensionsMap.putAll(extensions);
            }

            if (errorClassification != null) {
                if (errorClassification instanceof ErrorType) {
                    extensionsMap.put("errorType", String.valueOf(errorClassification));
                } else if (errorClassification instanceof ErrorDetail) {
                    extensionsMap.put("errorType", String.valueOf(((ErrorDetail) errorClassification).getErrorType()));
                    extensionsMap.put("errorDetail", String.valueOf(errorClassification));
                } else if (!extensionsMap.containsKey("classification")) {
                    extensionsMap.put("classification", String.valueOf(errorClassification));
                }
            }
            if (origin != null) extensionsMap.put("origin", origin);
            if (debugUri != null) extensionsMap.put("debugUri", debugUri);
            if (debugInfo != null) extensionsMap.put("debugInfo", debugInfo);
            return extensionsMap;
        }

        public Builder message(String message) {
            this.message = assertNotNull(message);
            return this;
        }

        @Override
        public Builder message(String message, Object... formatArgs) {
            if (formatArgs == null || formatArgs.length == 0) {
                this.message = assertNotNull(message);
            } else {
                this.message = String.format(assertNotNull(message), formatArgs);
            }
            return this;
        }

        @Override
        public Builder locations(@Nullable List<SourceLocation> locations) {
            if (locations != null) {
                if (this.locations == null) {
                    this.locations = new ArrayList<>(locations);
                } else {
                    this.locations.addAll(locations);
                }
            } else {
                this.locations = null;
            }
            return this;
        }

        @Override
        public Builder location(@Nullable SourceLocation location) {
            if (location != null) {
                if (this.locations == null) {
                    this.locations = new ArrayList<>();
                }
                this.locations.add(location);
            }
            return this;
        }

        @Override
        public Builder path(@Nullable ResultPath path) {
            if (path != null) {
                this.path = path.toList();
            } else {
                this.path = null;
            }
            return this;
        }

        @Override
        public Builder path(@Nullable List<Object> path) {
            this.path = path;
            return this;
        }

        public Builder errorType(ErrorType errorType) {
            this.errorClassification = assertNotNull(errorType);
            return this;
        }

        public Builder errorDetail(ErrorDetail errorDetail) {
            this.errorClassification = assertNotNull(errorDetail);
            return this;
        }

        public Builder origin(String origin) {
            this.origin = assertNotNull(origin);
            return this;
        }

        public Builder debugUri(String debugUri) {
            this.debugUri = assertNotNull(debugUri);
            return this;
        }

        public Builder debugInfo(Map<String, Object> debugInfo) {
            this.debugInfo = assertNotNull(debugInfo);
            return this;
        }

        @Override
        public Builder extensions(@Nullable Map<String, Object> extensions) {
            this.extensions = extensions;
            return this;
        }

        @Override
        public Builder errorType(ErrorClassification errorType) {
            this.errorClassification = errorType;
            return this;
        }

        /**
         * @return a newly built GraphQLError
         */
        public TypedGraphQLError build() {
            if (message == null) message = defaultMessage();
            return new TypedGraphQLError(message, locations, errorClassification, path, getExtensions());
        }
    }

    /**
     * Creates a new, empty LinkedHashMap suitable for the expected number of mappings.
     */
    private static <K, V> LinkedHashMap<K, V> newLinkedHashMap(int numMappings) {
        return new LinkedHashMap<>((int) Math.ceil(numMappings / (double) 0.75f));
    }
}
