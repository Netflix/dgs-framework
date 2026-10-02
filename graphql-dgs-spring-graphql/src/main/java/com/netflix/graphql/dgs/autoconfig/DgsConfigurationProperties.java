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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;
import java.util.Objects;

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
            @NotNull @DefaultValue(DgsSchemaProvider.DEFAULT_SCHEMA_LOCATION) List<String> schemaLocations,
            @DefaultValue("true") boolean schemaWiringValidationEnabled,
            @DefaultValue("false") boolean enableEntityFetcherCustomScalarParsing,
            @NotNull @DefaultValue DgsPreparsedDocumentProviderConfigurationProperties preparsedDocumentProvider,
            @NotNull @DefaultValue DgsIntrospectionConfigurationProperties introspection,
            @NotNull @DefaultValue DgsStrictModeProperties strictMode,
            @NotNull @DefaultValue DgsFederationProperties federation) {
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

    @NotNull
    public List<String> getSchemaLocations() {
        return schemaLocations;
    }

    public boolean isSchemaWiringValidationEnabled() {
        return schemaWiringValidationEnabled;
    }

    /** Retained for compatibility with the former Kotlin property getter. */
    public boolean getSchemaWiringValidationEnabled() {
        return schemaWiringValidationEnabled;
    }

    public boolean isEnableEntityFetcherCustomScalarParsing() {
        return enableEntityFetcherCustomScalarParsing;
    }

    /** Retained for compatibility with the former Kotlin property getter. */
    public boolean getEnableEntityFetcherCustomScalarParsing() {
        return enableEntityFetcherCustomScalarParsing;
    }

    @NotNull
    public DgsPreparsedDocumentProviderConfigurationProperties getPreparsedDocumentProvider() {
        return preparsedDocumentProvider;
    }

    @NotNull
    public DgsIntrospectionConfigurationProperties getIntrospection() {
        return introspection;
    }

    @NotNull
    public DgsStrictModeProperties getStrictMode() {
        return strictMode;
    }

    @NotNull
    public DgsFederationProperties getFederation() {
        return federation;
    }

    @NotNull
    public List<String> component1() {
        return schemaLocations;
    }

    public boolean component2() {
        return schemaWiringValidationEnabled;
    }

    public boolean component3() {
        return enableEntityFetcherCustomScalarParsing;
    }

    @NotNull
    public DgsPreparsedDocumentProviderConfigurationProperties component4() {
        return preparsedDocumentProvider;
    }

    @NotNull
    public DgsIntrospectionConfigurationProperties component5() {
        return introspection;
    }

    @NotNull
    public DgsStrictModeProperties component6() {
        return strictMode;
    }

    @NotNull
    public DgsFederationProperties component7() {
        return federation;
    }

    @NotNull
    public DgsConfigurationProperties copy(
            @NotNull List<String> schemaLocations,
            boolean schemaWiringValidationEnabled,
            boolean enableEntityFetcherCustomScalarParsing,
            @NotNull DgsPreparsedDocumentProviderConfigurationProperties preparsedDocumentProvider,
            @NotNull DgsIntrospectionConfigurationProperties introspection,
            @NotNull DgsStrictModeProperties strictMode,
            @NotNull DgsFederationProperties federation) {
        return new DgsConfigurationProperties(
                schemaLocations,
                schemaWiringValidationEnabled,
                enableEntityFetcherCustomScalarParsing,
                preparsedDocumentProvider,
                introspection,
                strictMode,
                federation);
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof DgsConfigurationProperties that
                && schemaWiringValidationEnabled == that.schemaWiringValidationEnabled
                && enableEntityFetcherCustomScalarParsing == that.enableEntityFetcherCustomScalarParsing
                && Objects.equals(schemaLocations, that.schemaLocations)
                && Objects.equals(preparsedDocumentProvider, that.preparsedDocumentProvider)
                && Objects.equals(introspection, that.introspection)
                && Objects.equals(strictMode, that.strictMode)
                && Objects.equals(federation, that.federation);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(schemaLocations);
        result = 31 * result + Boolean.hashCode(schemaWiringValidationEnabled);
        result = 31 * result + Boolean.hashCode(enableEntityFetcherCustomScalarParsing);
        result = 31 * result + Objects.hashCode(preparsedDocumentProvider);
        result = 31 * result + Objects.hashCode(introspection);
        result = 31 * result + Objects.hashCode(strictMode);
        result = 31 * result + Objects.hashCode(federation);
        return result;
    }

    @NotNull
    @Override
    public String toString() {
        return "DgsConfigurationProperties(schemaLocations=" + schemaLocations
                + ", schemaWiringValidationEnabled=" + schemaWiringValidationEnabled
                + ", enableEntityFetcherCustomScalarParsing=" + enableEntityFetcherCustomScalarParsing
                + ", preparsedDocumentProvider=" + preparsedDocumentProvider
                + ", introspection=" + introspection + ", strictMode=" + strictMode
                + ", federation=" + federation + ")";
    }

    public static class DgsPreparsedDocumentProviderConfigurationProperties {
        private final boolean enabled;
        private final long maximumCacheSize;

        /**
         * How long cache entries are valid for since creation, replacement or last access, specified with an
         * ISO-8601 duration string.
         */
        private final String cacheValidityDuration;

        @ConstructorBinding
        public DgsPreparsedDocumentProviderConfigurationProperties(
                @DefaultValue("false") boolean enabled,
                @DefaultValue("2000") long maximumCacheSize,
                @NotNull @DefaultValue("PT1H") String cacheValidityDuration) {
            this.enabled = enabled;
            this.maximumCacheSize = maximumCacheSize;
            this.cacheValidityDuration = cacheValidityDuration != null ? cacheValidityDuration : "PT1H";
        }

        public DgsPreparsedDocumentProviderConfigurationProperties() {
            this(false, 2000, "PT1H");
        }

        public boolean isEnabled() {
            return enabled;
        }

        /** Retained for compatibility with the former Kotlin property getter. */
        public boolean getEnabled() {
            return enabled;
        }

        public long getMaximumCacheSize() {
            return maximumCacheSize;
        }

        @NotNull
        public String getCacheValidityDuration() {
            return cacheValidityDuration;
        }

        public boolean component1() {
            return enabled;
        }

        public long component2() {
            return maximumCacheSize;
        }

        @NotNull
        public String component3() {
            return cacheValidityDuration;
        }

        @NotNull
        public DgsPreparsedDocumentProviderConfigurationProperties copy(
                boolean enabled, long maximumCacheSize, @NotNull String cacheValidityDuration) {
            return new DgsPreparsedDocumentProviderConfigurationProperties(enabled, maximumCacheSize, cacheValidityDuration);
        }

        @Override
        public boolean equals(@Nullable Object other) {
            if (this == other) {
                return true;
            }
            return other instanceof DgsPreparsedDocumentProviderConfigurationProperties that
                    && enabled == that.enabled
                    && maximumCacheSize == that.maximumCacheSize
                    && Objects.equals(cacheValidityDuration, that.cacheValidityDuration);
        }

        @Override
        public int hashCode() {
            int result = Boolean.hashCode(enabled);
            result = 31 * result + Long.hashCode(maximumCacheSize);
            result = 31 * result + Objects.hashCode(cacheValidityDuration);
            return result;
        }

        @NotNull
        @Override
        public String toString() {
            return "DgsPreparsedDocumentProviderConfigurationProperties(enabled=" + enabled
                    + ", maximumCacheSize=" + maximumCacheSize
                    + ", cacheValidityDuration=" + cacheValidityDuration + ")";
        }
    }

    public static class DgsIntrospectionConfigurationProperties {
        /**
         * Due to legacy reasons, SDL comments (i.e. # comments) are shown in introspection queries by default.
         * This property toggles that visibility.
         */
        private final boolean showSdlComments;

        @ConstructorBinding
        public DgsIntrospectionConfigurationProperties(@DefaultValue("true") boolean showSdlComments) {
            this.showSdlComments = showSdlComments;
        }

        public DgsIntrospectionConfigurationProperties() {
            this(true);
        }

        public boolean isShowSdlComments() {
            return showSdlComments;
        }

        /** Retained for compatibility with the former Kotlin property getter. */
        public boolean getShowSdlComments() {
            return showSdlComments;
        }

        public boolean component1() {
            return showSdlComments;
        }

        @NotNull
        public DgsIntrospectionConfigurationProperties copy(boolean showSdlComments) {
            return new DgsIntrospectionConfigurationProperties(showSdlComments);
        }

        @Override
        public boolean equals(@Nullable Object other) {
            return this == other
                    || other instanceof DgsIntrospectionConfigurationProperties that
                            && showSdlComments == that.showSdlComments;
        }

        @Override
        public int hashCode() {
            return Boolean.hashCode(showSdlComments);
        }

        @NotNull
        @Override
        public String toString() {
            return "DgsIntrospectionConfigurationProperties(showSdlComments=" + showSdlComments + ")";
        }
    }

    public static class DgsStrictModeProperties {
        private final boolean enabled;

        @ConstructorBinding
        public DgsStrictModeProperties(@DefaultValue("true") boolean enabled) {
            this.enabled = enabled;
        }

        public DgsStrictModeProperties() {
            this(true);
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
        public DgsStrictModeProperties copy(boolean enabled) {
            return new DgsStrictModeProperties(enabled);
        }

        @Override
        public boolean equals(@Nullable Object other) {
            return this == other || other instanceof DgsStrictModeProperties that && enabled == that.enabled;
        }

        @Override
        public int hashCode() {
            return Boolean.hashCode(enabled);
        }

        @NotNull
        @Override
        public String toString() {
            return "DgsStrictModeProperties(enabled=" + enabled + ")";
        }
    }

    public static class DgsFederationProperties {
        private final boolean enabled;

        @ConstructorBinding
        public DgsFederationProperties(@DefaultValue("true") boolean enabled) {
            this.enabled = enabled;
        }

        public DgsFederationProperties() {
            this(true);
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
        public DgsFederationProperties copy(boolean enabled) {
            return new DgsFederationProperties(enabled);
        }

        @Override
        public boolean equals(@Nullable Object other) {
            return this == other || other instanceof DgsFederationProperties that && enabled == that.enabled;
        }

        @Override
        public int hashCode() {
            return Boolean.hashCode(enabled);
        }

        @NotNull
        @Override
        public String toString() {
            return "DgsFederationProperties(enabled=" + enabled + ")";
        }
    }
}
