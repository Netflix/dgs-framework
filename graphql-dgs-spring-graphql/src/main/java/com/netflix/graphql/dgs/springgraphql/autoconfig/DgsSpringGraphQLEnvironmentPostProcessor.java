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
import org.springframework.boot.SpringBootVersion;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

public class DgsSpringGraphQLEnvironmentPostProcessor implements EnvironmentPostProcessor {
    private static final String SPRING_GRAPHQL_SCHEMA_INTROSPECTION_ENABLED =
            "spring.graphql.schema.introspection.enabled";
    private static final String DGS_GRAPHQL_INTROSPECTION_ENABLED = "dgs.graphql.introspection.enabled";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        springBootVersionCheck();

        Map<String, Object> properties = new HashMap<>();

        if (environment.getProperty(SPRING_GRAPHQL_SCHEMA_INTROSPECTION_ENABLED) != null
                && environment.getProperty(DGS_GRAPHQL_INTROSPECTION_ENABLED) != null) {
            throw new RuntimeException("Both properties `" + SPRING_GRAPHQL_SCHEMA_INTROSPECTION_ENABLED + "` and `"
                    + DGS_GRAPHQL_INTROSPECTION_ENABLED + "` are explicitly set. Use `"
                    + DGS_GRAPHQL_INTROSPECTION_ENABLED + "` only");
        } else if (environment.getProperty(DGS_GRAPHQL_INTROSPECTION_ENABLED) != null) {
            properties.put(
                    SPRING_GRAPHQL_SCHEMA_INTROSPECTION_ENABLED,
                    environment.getProperty(DGS_GRAPHQL_INTROSPECTION_ENABLED));
        } else {
            Object introspectionEnabled = environment.getProperty(SPRING_GRAPHQL_SCHEMA_INTROSPECTION_ENABLED);
            properties.put(
                    SPRING_GRAPHQL_SCHEMA_INTROSPECTION_ENABLED,
                    introspectionEnabled != null ? introspectionEnabled : true);
        }

        properties.put("spring.graphql.graphiql.enabled", propertyOrDefault(environment,
                "dgs.graphql.graphiql.enabled", true));
        properties.put("spring.graphql.graphiql.path", propertyOrDefault(environment,
                "dgs.graphql.graphiql.path", "/graphiql"));
        properties.put("spring.graphql.http.path", propertyOrDefault(environment, "dgs.graphql.path", "/graphql"));
        properties.put(
                "spring.graphql.websocket.connection-init-timeout",
                propertyOrDefault(environment, "dgs.graphql.websocket.connection-init-timeout", "10s"));

        String websocketPath = environment.getProperty("dgs.graphql.websocket.path");
        if (websocketPath != null) {
            properties.put("spring.graphql.websocket.path", websocketPath);
        }

        if (environment.getProperty("dgs.graphql.virtualthreads.enabled") == null
                && "true".equals(environment.getProperty("spring.threads.virtual.enabled"))) {
            properties.put("dgs.graphql.virtualthreads.enabled", true);
        }

        environment.getPropertySources().addLast(new MapPropertySource("dgs-spring-graphql-defaults", properties));
    }

    private static Object propertyOrDefault(ConfigurableEnvironment environment, String key, Object defaultValue) {
        Object value = environment.getProperty(key);
        return value != null ? value : defaultValue;
    }

    private void springBootVersionCheck() {
        String majorVersion = SpringBootVersion.getVersion().split("\\.")[0];
        if (Integer.parseInt(majorVersion) < 4) {
            throw new RuntimeException("DGS 11+ is only compatible with Spring Boot 4 and above. "
                    + "Use DGS 10.x for Spring Boot 3 compatibility");
        }
    }
}
