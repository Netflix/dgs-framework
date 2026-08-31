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

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "dgs.graphql.spring")
public class DgsSpringGraphQLConfigurationProperties {
    private final WebMvc webmvc;

    public DgsSpringGraphQLConfigurationProperties(@DefaultValue WebMvc webmvc) {
        this.webmvc = webmvc != null ? webmvc : new WebMvc(new Asyncdispatch(false));
    }

    public WebMvc getWebmvc() {
        return webmvc;
    }

    public static class WebMvc {
        private final Asyncdispatch asyncdispatch;

        public WebMvc(@DefaultValue Asyncdispatch asyncdispatch) {
            this.asyncdispatch = asyncdispatch != null ? asyncdispatch : new Asyncdispatch(false);
        }

        public Asyncdispatch getAsyncdispatch() {
            return asyncdispatch;
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
    }
}
