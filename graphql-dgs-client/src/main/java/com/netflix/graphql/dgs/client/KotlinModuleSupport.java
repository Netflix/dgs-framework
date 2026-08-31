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

package com.netflix.graphql.dgs.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Registers jackson-module-kotlin on the client's JSON mappers, but only when it is actually on
 * the classpath.
 *
 * <p>Kotlin consumers need this module: a Kotlin data class has no no-arg constructor, and its
 * default parameter values can only be honoured by the Kotlin module. (Both Jackson 2 with
 * ParameterNamesModule and Jackson 3 natively can construct an all-required-properties data class
 * from parameter names, which is why the absence of this module shows up only on defaults and
 * explicit nulls.)
 *
 * <p>Java-only consumers must not be forced to bring the module, so jackson-module-kotlin stays a
 * {@code compileOnly} dependency and every reference to a {@code KotlinModule} is isolated in a
 * nested holder class. A holder is only loaded when its register method runs, so a missing module
 * surfaces as a catchable {@link NoClassDefFoundError} rather than breaking this class'
 * initialisation.
 */
final class KotlinModuleSupport {

    private KotlinModuleSupport() {}

    /**
     * Registers the Jackson 2 Kotlin module on {@code mapper} if available, and returns
     * {@code mapper} either way so this can be chained onto a {@code new ObjectMapper()}.
     */
    static ObjectMapper registerIfAvailable(ObjectMapper mapper) {
        try {
            return Jackson2Registrar.register(mapper);
        } catch (NoClassDefFoundError | ExceptionInInitializerError e) {
            return mapper;
        }
    }

    /**
     * Adds the Jackson 3 Kotlin module to {@code builder} if available, and returns
     * {@code builder} either way.
     */
    static JsonMapper.Builder addIfAvailable(JsonMapper.Builder builder) {
        try {
            return Jackson3Registrar.add(builder);
        } catch (NoClassDefFoundError | ExceptionInInitializerError e) {
            return builder;
        }
    }

    private static final class Jackson2Registrar {
        private Jackson2Registrar() {}

        static ObjectMapper register(ObjectMapper mapper) {
            return mapper.registerModule(new com.fasterxml.jackson.module.kotlin.KotlinModule.Builder()
                    .enable(com.fasterxml.jackson.module.kotlin.KotlinFeature.NullIsSameAsDefault)
                    .build());
        }
    }

    private static final class Jackson3Registrar {
        private Jackson3Registrar() {}

        static JsonMapper.Builder add(JsonMapper.Builder builder) {
            return builder.addModule(new tools.jackson.module.kotlin.KotlinModule.Builder()
                    .enable(tools.jackson.module.kotlin.KotlinFeature.NullIsSameAsDefault)
                    .build());
        }
    }
}
