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

package com.netflix.graphql.dgs.internal.utils;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * This implementation has borrowed heavily from graphql-servlet-java implementation of the variable mapper.
 * It handles populating the query variables with the files specified by object paths in the multi-part request.
 * Specifically, it takes each entry here
 * {@code -F map='{ "0": ["variables.input.files.0"], "1": ["variables.input.files.1"] }'}, and uses the object path,
 * e.g. {@code variables.input.files.0}, to navigate to the appropriate path in the query variables, i.e.
 * {@code "variables": { "input": { "description": "test", "files": [null, null] } } }} and sets it to the file
 * specified as {@code -F '0=@file1.txt' -F '1=@file2.txt'}.
 *
 * <p>The resulting map of populated query variables is the output.
 */
public final class MultipartVariableMapper {
    private static final Pattern PERIOD = Pattern.compile("\\.");

    private static final Mapper<Map<String, Object>> MAP_MAPPER = new Mapper<>() {
        @Override
        public Object set(Map<String, Object> location, String target, MultipartFile value) {
            return location.put(target, value);
        }

        @Override
        public Object recurse(Map<String, Object> location, String target) {
            Object value = location.get(target);
            if (value == null) {
                throw new VariableMappingException("Path not found: " + target);
            }
            return value;
        }
    };

    private static final Mapper<List<Object>> LIST_MAPPER = new Mapper<>() {
        @Override
        public Object set(List<Object> location, String target, MultipartFile value) {
            return location.set(Integer.parseInt(target), value);
        }

        @Override
        public Object recurse(List<Object> location, String target) {
            return location.get(Integer.parseInt(target));
        }
    };

    private MultipartVariableMapper() {
    }

    interface Mapper<T> {
        Object set(T location, String target, MultipartFile value);

        Object recurse(T location, String target);
    }

    @SuppressWarnings("unchecked")
    public static void mapVariable(String objectPath, Map<String, Object> variables, MultipartFile part) {
        String[] segments = PERIOD.split(objectPath);

        if (segments.length < 2) {
            throw new VariableMappingException("object-path in map must have at least two segments");
        } else if (!"variables".equals(segments[0])) {
            throw new VariableMappingException("can only map into variables");
        }

        Object currentLocation = variables;
        for (int i = 1; i < segments.length; i++) {
            String segmentName = segments[i];
            if (i == segments.length - 1) {
                if (currentLocation instanceof Map<?, ?>) {
                    if (MAP_MAPPER.set((Map<String, Object>) currentLocation, segmentName, part) != null) {
                        throw new VariableMappingException("expected null value when mapping " + objectPath);
                    }
                } else {
                    if (LIST_MAPPER.set((List<Object>) currentLocation, segmentName, part) != null) {
                        throw new VariableMappingException("expected null value when mapping " + objectPath);
                    }
                }
            } else {
                currentLocation = currentLocation instanceof Map<?, ?>
                        ? MAP_MAPPER.recurse((Map<String, Object>) currentLocation, segmentName)
                        : LIST_MAPPER.recurse((List<Object>) currentLocation, segmentName);
            }
        }
    }
}
