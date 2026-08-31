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

package com.netflix.graphql.dgs.autoconfig;

import com.netflix.graphql.dgs.internal.DgsSchemaProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/** Configuration properties for DGS framework. */
@ConfigurationProperties(prefix = "dgs.graphql")
public class DgsConfigurationProperties {
    /** Location of the GraphQL schema files. */
    private final List<String> schemaLocations;

    private final boolean schemaWiringValidationEnabled;
    private final boolean enableEntityFetcherCustomScalarParsing;
    private final DgsPreparsedDocumentProviderConfigurationProperties preparsedDocumentProvider;
    private final DgsIntrospectionConfigurationProperties introspection;
    private final DgsStrictModeProperties strictMode;
    private final DgsFederationProperties federation;

    public DgsConfigurationProperties(
            @DefaultValue(DgsSchemaProvider.DEFAULT_SCHEMA_LOCATION) List<String> schemaLocations,
            @DefaultValue("true") boolean schemaWiringValidationEnabled,
            @DefaultValue("false") boolean enableEntityFetcherCustomScalarParsing,
            @DefaultValue DgsPreparsedDocumentProviderConfigurationProperties preparsedDocumentProvider,
            @DefaultValue DgsIntrospectionConfigurationProperties introspection,
            @DefaultValue DgsStrictModeProperties strictMode,
            @DefaultValue DgsFederationProperties federation) {
        this.schemaLocations = schemaLocations;
        this.schemaWiringValidationEnabled = schemaWiringValidationEnabled;
        this.enableEntityFetcherCustomScalarParsing = enableEntityFetcherCustomScalarParsing;
        this.preparsedDocumentProvider = preparsedDocumentProvider != null
                ? preparsedDocumentProvider
                : new DgsPreparsedDocumentProviderConfigurationProperties(false, 2000, "PT1H");
        this.introspection = introspection != null ? introspection : new DgsIntrospectionConfigurationProperties(true);
        this.strictMode = strictMode != null ? strictMode : new DgsStrictModeProperties(true);
        this.federation = federation != null ? federation : new DgsFederationProperties(true);
    }

    public List<String> getSchemaLocations() {
        return schemaLocations;
    }

    public boolean isSchemaWiringValidationEnabled() {
        return schemaWiringValidationEnabled;
    }

    public boolean isEnableEntityFetcherCustomScalarParsing() {
        return enableEntityFetcherCustomScalarParsing;
    }

    public DgsPreparsedDocumentProviderConfigurationProperties getPreparsedDocumentProvider() {
        return preparsedDocumentProvider;
    }

    public DgsIntrospectionConfigurationProperties getIntrospection() {
        return introspection;
    }

    public DgsStrictModeProperties getStrictMode() {
        return strictMode;
    }

    public DgsFederationProperties getFederation() {
        return federation;
    }

    public static class DgsPreparsedDocumentProviderConfigurationProperties {
        private final boolean enabled;
        private final long maximumCacheSize;

        /**
         * How long cache entries are valid for since creation, replacement or last access, specified with an
         * ISO-8601 duration string.
         */
        private final String cacheValidityDuration;

        public DgsPreparsedDocumentProviderConfigurationProperties(
                @DefaultValue("false") boolean enabled,
                @DefaultValue("2000") long maximumCacheSize,
                @DefaultValue("PT1H") String cacheValidityDuration) {
            this.enabled = enabled;
            this.maximumCacheSize = maximumCacheSize;
            this.cacheValidityDuration = cacheValidityDuration != null ? cacheValidityDuration : "PT1H";
        }

        public boolean isEnabled() {
            return enabled;
        }

        public long getMaximumCacheSize() {
            return maximumCacheSize;
        }

        public String getCacheValidityDuration() {
            return cacheValidityDuration;
        }
    }

    public static class DgsIntrospectionConfigurationProperties {
        /**
         * Due to legacy reasons, SDL comments (i.e. # comments) are shown in introspection queries by default.
         * This property toggles that visibility.
         */
        private final boolean showSdlComments;

        public DgsIntrospectionConfigurationProperties(@DefaultValue("true") boolean showSdlComments) {
            this.showSdlComments = showSdlComments;
        }

        public boolean isShowSdlComments() {
            return showSdlComments;
        }
    }

    public static class DgsStrictModeProperties {
        private final boolean enabled;

        public DgsStrictModeProperties(@DefaultValue("true") boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isEnabled() {
            return enabled;
        }
    }

    public static class DgsFederationProperties {
        private final boolean enabled;

        public DgsFederationProperties(@DefaultValue("true") boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isEnabled() {
            return enabled;
        }
    }
}
