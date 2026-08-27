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

package com.netflix.graphql.dgs.springgraphql.autoconfig;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/** Globally disable AutoConfig's which cause problems in the Netflix environment. */
@Order(Ordered.LOWEST_PRECEDENCE - 1)
public class ExcludeAutoConfigurationsEnvironmentPostProcessor implements EnvironmentPostProcessor {
    private static final Map<String, String> DISABLE_AUTOCONFIG_PROPERTIES = new LinkedHashMap<>();

    private static final String EXCLUDE = "spring.autoconfigure.exclude";
    private static final String INLINED_TEST_PROPERTIES = "Inlined Test Properties";

    static {
        DISABLE_AUTOCONFIG_PROPERTIES.put(
                "dgs.springgraphql.autoconfiguration.graphqlobservation.enabled",
                "org.springframework.boot.graphql.autoconfigure.observation.GraphQlObservationAutoConfiguration");
        DISABLE_AUTOCONFIG_PROPERTIES.put(
                "dgs.springgraphql.autoconfiguration.graphqlwebmvcsecurity.enabled",
                "org.springframework.boot.graphql.autoconfigure.security.GraphQlWebMvcSecurityAutoConfiguration");
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String existingExcludes = extractAllExcludes(environment.getPropertySources());
        List<String> values = new ArrayList<>();
        DISABLE_AUTOCONFIG_PROPERTIES.forEach((property, autoConfiguration) -> {
            if (!environment.getProperty(property, Boolean.class, false)) {
                values.add(autoConfiguration);
            }
        });
        values.add(existingExcludes);
        String disabled = values.stream().filter(value -> !value.isEmpty()).collect(Collectors.joining(","));

        environment
                .getPropertySources()
                .addFirst(new MapPropertySource("disableRefreshScope", Map.of(EXCLUDE, disabled)));
    }

    private String extractAllExcludes(MutablePropertySources propertySources) {
        PropertySource<?> testProperties = propertySources.get(INLINED_TEST_PROPERTIES);
        if (testProperties != null) {
            List<String> testExclude = excludesFrom(testProperties);
            if (!testExclude.isEmpty()) {
                return String.join(",", testExclude);
            }
        }

        return StreamSupport.stream(propertySources.spliterator(), false)
                .filter(src -> !ConfigurationPropertySources.isAttachedConfigurationPropertySource(src))
                .flatMap(src -> excludesFrom(src).stream())
                .collect(Collectors.joining(","));
    }

    /**
     * Collects exclude class names from a property source.
     *
     * <p>Spring Boot accepts several representations of {@code spring.autoconfigure.exclude}:
     * a comma-separated string, a YAML list (stored as a {@link Collection} or array), and index-based keys such as
     * {@code spring.autoconfigure.exclude[0]}. Only the string/array forms were previously merged, so user YAML lists
     * and indexed properties were overwritten by this post-processor.
     */
    private List<String> excludesFrom(PropertySource<?> src) {
        Object property = src.getProperty(EXCLUDE);
        List<String> fromDirect = List.of();
        if (property instanceof String stringProperty) {
            fromDirect = Arrays.stream(stringProperty.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .toList();
        } else if (property instanceof Object[] arrayProperty) {
            fromDirect = Arrays.stream(arrayProperty)
                    .filter(String.class::isInstance)
                    .map(value -> ((String) value).trim())
                    .filter(value -> !value.isEmpty())
                    .toList();
        } else if (property instanceof Collection<?> collectionProperty) {
            fromDirect = collectionProperty.stream()
                    .filter(String.class::isInstance)
                    .map(value -> ((String) value).trim())
                    .filter(value -> !value.isEmpty())
                    .toList();
        }
        if (!fromDirect.isEmpty()) {
            return fromDirect;
        }

        List<String> indexed = new ArrayList<>();
        int i = 0;
        while (true) {
            Object value = src.getProperty(EXCLUDE + "[" + i + "]");
            if (value == null) {
                break;
            }
            String str = value.toString().trim();
            if (!str.isEmpty()) {
                indexed.add(str);
            }
            i++;
        }
        return indexed;
    }
}
