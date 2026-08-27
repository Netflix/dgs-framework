/*
 * Copyright 2026 Netflix, Inc.
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

package com.netflix.graphql.dgs.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.kotlin.KotlinModule;

/**
 * Registers jackson-module-kotlin on the deprecated Jackson 2 mapper of
 * {@link BaseDgsQueryExecutor}, but only when it is actually on the classpath.
 *
 * <p>Kotlin consumers need this module: a Kotlin data class has no no-arg constructor. Java-only
 * consumers must not be forced to bring it, so jackson-module-kotlin stays a {@code compileOnly}
 * dependency and the reference to {@link KotlinModule} is isolated in the nested
 * {@code Registrar} class, which is only loaded when {@link #registerIfAvailable} runs.
 */
final class Jackson2KotlinModuleSupport {

    private Jackson2KotlinModuleSupport() {}

    /**
     * Registers the Kotlin module on {@code mapper} if available, and returns {@code mapper}
     * either way so this can be chained onto a {@code new ObjectMapper()}.
     */
    static ObjectMapper registerIfAvailable(ObjectMapper mapper) {
        try {
            return Registrar.register(mapper);
        } catch (NoClassDefFoundError | ExceptionInInitializerError e) {
            // jackson-module-kotlin (or kotlin-reflect) is not on the classpath: nothing to do.
            return mapper;
        }
    }

    private static final class Registrar {
        private Registrar() {}

        static ObjectMapper register(ObjectMapper mapper) {
            // Default KotlinModule settings, matching the jacksonObjectMapper() this replaced.
            return mapper.registerModule(new KotlinModule.Builder().build());
        }
    }
}
