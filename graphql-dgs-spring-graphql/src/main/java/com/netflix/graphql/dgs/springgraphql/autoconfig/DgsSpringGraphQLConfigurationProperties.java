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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Objects;

@ConfigurationProperties(prefix = "dgs.graphql.spring")
public class DgsSpringGraphQLConfigurationProperties {
    private final WebMvc webmvc;

    @ConstructorBinding
    public DgsSpringGraphQLConfigurationProperties(@NotNull @DefaultValue WebMvc webmvc) {
        this.webmvc = webmvc != null ? webmvc : new WebMvc(new Asyncdispatch(false));
    }

    public DgsSpringGraphQLConfigurationProperties() {
        this(new WebMvc(new Asyncdispatch(false)));
    }

    @NotNull
    public WebMvc getWebmvc() {
        return webmvc;
    }

    @NotNull
    public WebMvc component1() {
        return webmvc;
    }

    @NotNull
    public DgsSpringGraphQLConfigurationProperties copy(@NotNull WebMvc webmvc) {
        return new DgsSpringGraphQLConfigurationProperties(webmvc);
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return this == other || other instanceof DgsSpringGraphQLConfigurationProperties that
                && Objects.equals(webmvc, that.webmvc);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(webmvc);
    }

    @NotNull
    @Override
    public String toString() {
        return "DgsSpringGraphQLConfigurationProperties(webmvc=" + webmvc + ")";
    }

    public static class WebMvc {
        private final Asyncdispatch asyncdispatch;

        public WebMvc(@NotNull @DefaultValue Asyncdispatch asyncdispatch) {
            this.asyncdispatch = asyncdispatch != null ? asyncdispatch : new Asyncdispatch(false);
        }

        @NotNull
        public Asyncdispatch getAsyncdispatch() {
            return asyncdispatch;
        }

        @NotNull
        public Asyncdispatch component1() {
            return asyncdispatch;
        }

        @NotNull
        public WebMvc copy(@NotNull Asyncdispatch asyncdispatch) {
            return new WebMvc(asyncdispatch);
        }

        @Override
        public boolean equals(@Nullable Object other) {
            return this == other || other instanceof WebMvc that
                    && Objects.equals(asyncdispatch, that.asyncdispatch);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(asyncdispatch);
        }

        @NotNull
        @Override
        public String toString() {
            return "WebMvc(asyncdispatch=" + asyncdispatch + ")";
        }
    }

    public static class Asyncdispatch {
        private final boolean enabled;

        public Asyncdispatch(@DefaultValue("false") boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isEnabled() {
            return enabled;
        }

        /** Retained for compatibility with the former Kotlin property getter. */
        public boolean getEnabled() {
            return enabled;
        }

        public boolean component1() {
            return enabled;
        }

        @NotNull
        public Asyncdispatch copy(boolean enabled) {
            return new Asyncdispatch(enabled);
        }

        @Override
        public boolean equals(@Nullable Object other) {
            return this == other || other instanceof Asyncdispatch that && enabled == that.enabled;
        }

        @Override
        public int hashCode() {
            return Boolean.hashCode(enabled);
        }

        @NotNull
        @Override
        public String toString() {
            return "Asyncdispatch(enabled=" + enabled + ")";
        }
    }
}
