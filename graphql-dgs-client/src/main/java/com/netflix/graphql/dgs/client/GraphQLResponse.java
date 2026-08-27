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

package com.netflix.graphql.dgs.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.TypeRef;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import org.intellij.lang.annotations.Language;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @deprecated Tied to Jackson 2. Program against the {@link DgsGraphQLResponse} interface instead; the new
 *             {@code Dgs*} client classes return it. This class will be removed in a future release.
 */
@Deprecated
public class GraphQLResponse implements DgsGraphQLResponse {
    private static final Logger logger = LoggerFactory.getLogger(GraphQLResponse.class);

    private static final TypeRef<List<GraphQLError>> ERRORS_TYPE = new TypeRef<>() {};

    /**
     * @deprecated use {@link GraphQLRequestOptions#createCustomObjectMapper}
     */
    @Deprecated
    static final ObjectMapper DEFAULT_MAPPER =
            new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .registerModule(new ParameterNamesModule())
                    .registerModule(new Jdk8Module())
                    .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE);

    private final String json;
    private final Map<String, List<String>> headers;
    private final ObjectMapper mapper;
    private final DocumentContext parsed;
    private final Map<String, Object> data;
    private final List<GraphQLError> errors;

    public GraphQLResponse(
            @Language("json") String json, Map<String, List<String>> headers, ObjectMapper mapper) {
        this.json = json;
        this.headers = headers;
        this.mapper = mapper;
        this.parsed =
                JsonPath
                        .using(Configuration
                                .builder()
                                .jsonProvider(new JacksonJsonProvider(mapper))
                                .mappingProvider(new JacksonMappingProvider(mapper))
                                .build()
                                .addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL))
                        .parse(json);

        Map<String, Object> readData = parsed.read("data");
        this.data = readData != null ? readData : Map.of();
        List<GraphQLError> readErrors = parsed.read("errors", ERRORS_TYPE);
        this.errors = readErrors != null ? readErrors : List.of();
    }

    public GraphQLResponse(@Language("json") String json) {
        this(json, Map.of());
    }

    public GraphQLResponse(@Language("json") String json, Map<String, List<String>> headers) {
        this(json, headers, GraphQLRequestOptions.createCustomObjectMapper());
    }

    public GraphQLResponse(
            @Language("json") String json, Map<String, List<String>> headers, GraphQLRequestOptions options) {
        this(json, headers, GraphQLRequestOptions.createCustomObjectMapper(options));
    }

    @Override
    public String getJson() {
        return json;
    }

    @Override
    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    /** A JsonPath DocumentContext. Typically, only used internally. */
    @Override
    public DocumentContext getParsed() {
        return parsed;
    }

    /** Map representation of data. */
    @Override
    public Map<String, Object> getData() {
        return data;
    }

    @Override
    public List<GraphQLError> getErrors() {
        return errors;
    }

    /**
     * Deserialize data into the given class.
     * The class may need Jackson annotations for correct mapping.
     */
    @Override
    public <T> T dataAsObject(Class<T> clazz) {
        return mapper.convertValue(data, clazz);
    }

    /**
     * Extract values given a JsonPath. The return type will be whatever type you expect.
     * Although this looks type safe, it really isn't. Make sure values map to the expected type.
     * For JSON objects, a Map is returned. If you want to deserialize to a class, use
     * {@link #extractValueAsObject} instead.
     */
    @Override
    public <T> T extractValue(String path) {
        String dataPath = getDataPath(path);
        try {
            return parsed.read(dataPath);
        } catch (Exception ex) {
            logger.warn("Error extracting path '{}' from data: '{}'", path, data);
            throw ex;
        }
    }

    /** Extract values given a JsonPath and deserialize into the given class. */
    @Override
    public <T> T extractValueAsObject(String path, Class<T> clazz) {
        String dataPath = getDataPath(path);
        try {
            return parsed.read(dataPath, clazz);
        } catch (Exception ex) {
            logger.warn("Error extracting path '{}' from data: '{}'", path, data);
            throw ex;
        }
    }

    /**
     * Extract values given a JsonPath and deserialize into the given TypeRef.
     * Use this for Lists of a specific type.
     */
    @Override
    public <T> T extractValueAsObject(String path, TypeRef<T> typeRef) {
        String dataPath = getDataPath(path);
        try {
            return parsed.read(dataPath, typeRef);
        } catch (Exception ex) {
            logger.warn("Error extracting path '{}' from data: '{}'", path, data);
            throw ex;
        }
    }

    /**
     * Extracts RequestDetails from the response if available.
     * Returns null otherwise.
     */
    @Override
    public RequestDetails getRequestDetails() {
        return extractValueAsObject("gatewayRequestDetails", RequestDetails.class);
    }

    @Override
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /**
     * @deprecated Use {@link DgsGraphQLResponse#getDataPath} instead.
     */
    @Deprecated
    public static String getDataPath(String path) {
        return DgsGraphQLResponse.getDataPath(path);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof GraphQLResponse that
                && Objects.equals(json, that.json)
                && Objects.equals(headers, that.headers)
                && Objects.equals(mapper, that.mapper);
    }

    @Override
    public int hashCode() {
        return Objects.hash(json, headers, mapper);
    }

    @Override
    public String toString() {
        return "GraphQLResponse(json=" + json + ", headers=" + headers + ")";
    }
}
