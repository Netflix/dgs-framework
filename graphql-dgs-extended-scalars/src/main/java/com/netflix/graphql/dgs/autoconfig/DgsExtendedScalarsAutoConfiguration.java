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

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsRuntimeWiring;
import graphql.scalars.ExtendedScalars;
import graphql.schema.GraphQLScalarType;
import graphql.schema.idl.RuntimeWiring;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ConfigurationCondition;

import java.util.List;

@ConditionalOnClass(ExtendedScalars.class)
@ConditionalOnProperty(
        prefix = "dgs.graphql.extensions.scalars",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@AutoConfiguration
public class DgsExtendedScalarsAutoConfiguration {
    @ConditionalOnProperty(
            prefix = "dgs.graphql.extensions.scalars.time-dates",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    @Configuration(proxyBeanMethods = false)
    public static class TimeExtendedScalarsAutoConfiguration {
        @Bean
        public ExtendedScalarRegistrar timesExtendedScalarsRegistrar() {
            return new AbstractExtendedScalarRegistrar() {
                @Override
                public List<GraphQLScalarType> getScalars() {
                    return List.of(
                            ExtendedScalars.DateTime,
                            ExtendedScalars.Date,
                            ExtendedScalars.Time,
                            ExtendedScalars.LocalTime);
                }
            };
        }
    }

    @ConditionalOnProperty(
            prefix = "dgs.graphql.extensions.scalars.objects",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    @Configuration(proxyBeanMethods = false)
    public static class ObjectsExtendedScalarsAutoConfiguration {
        @Bean
        public ExtendedScalarRegistrar objectsExtendedScalarsRegistrar() {
            return new AbstractExtendedScalarRegistrar() {
                @Override
                public List<GraphQLScalarType> getScalars() {
                    return List.of(
                            ExtendedScalars.Object,
                            ExtendedScalars.Json,
                            ExtendedScalars.Url,
                            ExtendedScalars.Locale);
                }
            };
        }
    }

    @ConditionalOnProperty(
            prefix = "dgs.graphql.extensions.scalars.numbers",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    @Configuration(proxyBeanMethods = false)
    public static class NumbersExtendedScalarsAutoConfiguration {
        @Bean
        public ExtendedScalarRegistrar numbersExtendedScalarsRegistrar() {
            return new AbstractExtendedScalarRegistrar() {
                @Override
                public List<GraphQLScalarType> getScalars() {
                    return List.of(
                            // Integers
                            ExtendedScalars.PositiveInt,
                            ExtendedScalars.NegativeInt,
                            ExtendedScalars.NonNegativeInt,
                            ExtendedScalars.NonPositiveInt,
                            // Floats
                            ExtendedScalars.PositiveFloat,
                            ExtendedScalars.NegativeFloat,
                            ExtendedScalars.NonNegativeFloat,
                            ExtendedScalars.NonPositiveFloat,
                            // Others
                            ExtendedScalars.GraphQLLong,
                            ExtendedScalars.GraphQLShort,
                            ExtendedScalars.GraphQLByte);
                }
            };
        }

        @Conditional(OnBigDecimalAndNumbers.class)
        @Configuration(proxyBeanMethods = false)
        public static class BigDecimalAutoConfiguration {
            @Bean
            public ExtendedScalarRegistrar bigDecimalExtendedScalarsRegistrar() {
                return new AbstractExtendedScalarRegistrar() {
                    @Override
                    public List<GraphQLScalarType> getScalars() {
                        // Others
                        return List.of(ExtendedScalars.GraphQLBigDecimal);
                    }
                };
            }
        }

        public static class OnBigDecimalAndNumbers extends AllNestedConditions {
            public OnBigDecimalAndNumbers() {
                super(ConfigurationCondition.ConfigurationPhase.PARSE_CONFIGURATION);
            }

            @ConditionalOnProperty(
                    prefix = "dgs.graphql.extensions.scalars.numbers.",
                    name = "enabled",
                    havingValue = "true",
                    matchIfMissing = true)
            public static class OnNumbers {
            }

            @ConditionalOnProperty(
                    prefix = "dgs.graphql.extensions.scalars.numbers.bigdecimal",
                    name = "enabled",
                    havingValue = "true",
                    matchIfMissing = true)
            public static class OnBigDecimal {
            }
        }

        @Conditional(OnBigIntegerAndNumbers.class)
        @Configuration(proxyBeanMethods = false)
        public static class BigIntegerAutoConfiguration {
            @Bean
            public ExtendedScalarRegistrar bigIntegerExtendedScalarsRegistrar() {
                return new AbstractExtendedScalarRegistrar() {
                    @Override
                    public List<GraphQLScalarType> getScalars() {
                        return List.of(ExtendedScalars.GraphQLBigInteger);
                    }
                };
            }
        }

        public static class OnBigIntegerAndNumbers extends AllNestedConditions {
            public OnBigIntegerAndNumbers() {
                super(ConfigurationCondition.ConfigurationPhase.PARSE_CONFIGURATION);
            }

            @ConditionalOnProperty(
                    prefix = "dgs.graphql.extensions.scalars.numbers.",
                    name = "enabled",
                    havingValue = "true",
                    matchIfMissing = true)
            public static class OnNumbers {
            }

            @ConditionalOnProperty(
                    prefix = "dgs.graphql.extensions.scalars.numbers.biginteger",
                    name = "enabled",
                    havingValue = "true",
                    matchIfMissing = true)
            public static class OnBigInteger {
            }
        }
    }

    @ConditionalOnProperty(
            prefix = "dgs.graphql.extensions.scalars.currency",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    @Configuration(proxyBeanMethods = false)
    public static class CurrencyExtendedScalarsRegistrar {
        @Bean
        public ExtendedScalarRegistrar currencyExtendedScalarsRegistrar() {
            return new AbstractExtendedScalarRegistrar() {
                @Override
                public List<GraphQLScalarType> getScalars() {
                    return List.of(ExtendedScalars.Currency);
                }
            };
        }
    }

    @ConditionalOnProperty(
            prefix = "dgs.graphql.extensions.scalars.country",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    @Configuration(proxyBeanMethods = false)
    public static class CountryExtendedScalarsRegistrar {
        @Bean
        public ExtendedScalarRegistrar countryCodeExtendedScalarsRegistrar() {
            return new AbstractExtendedScalarRegistrar() {
                @Override
                public List<GraphQLScalarType> getScalars() {
                    return List.of(ExtendedScalars.CountryCode);
                }
            };
        }
    }

    @ConditionalOnProperty(
            prefix = "dgs.graphql.extensions.scalars.chars",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    @Configuration(proxyBeanMethods = false)
    public static class CharsExtendedScalarsAutoConfiguration {
        @Bean
        public ExtendedScalarRegistrar charsExtendedScalarsRegistrar() {
            return new AbstractExtendedScalarRegistrar() {
                @Override
                public List<GraphQLScalarType> getScalars() {
                    return List.of(ExtendedScalars.GraphQLChar);
                }
            };
        }
    }

    @ConditionalOnProperty(
            prefix = "dgs.graphql.extensions.scalars.ids",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    @Configuration(proxyBeanMethods = false)
    public static class IDsExtendedScalarsAutoConfiguration {
        @Bean
        public ExtendedScalarRegistrar idsExtendedScalarsRegistrar() {
            return new AbstractExtendedScalarRegistrar() {
                @Override
                public List<GraphQLScalarType> getScalars() {
                    return List.of(ExtendedScalars.UUID);
                }
            };
        }
    }

    @DgsComponent
    @FunctionalInterface
    public interface ExtendedScalarRegistrar {
        List<GraphQLScalarType> getScalars();
    }

    public abstract static class AbstractExtendedScalarRegistrar implements ExtendedScalarRegistrar {
        /**
         * Provide a mechanism to disable strict mode for scalar extensions; this will throw an exception if the scalar type is already registered.
         *
         * @see <a href="https://github.com/graphql-java/graphql-java/commit/09f6a88c36affb1de56b3fe74b2a792b50ed941c">graphql-java commit</a>
         */
        @Value("${dgs.graphql.extensions.scalars.strict-mode.enabled:false}")
        private boolean strictModeEnabled = false;

        @DgsRuntimeWiring
        public RuntimeWiring.Builder addScalar(RuntimeWiring.Builder builder) {
            RuntimeWiring.Builder acc = builder.strictMode(strictModeEnabled);
            List<GraphQLScalarType> scalars = getScalars();
            for (int i = scalars.size() - 1; i >= 0; i--) {
                acc = acc.scalar(scalars.get(i));
            }
            return acc;
        }
    }
}
