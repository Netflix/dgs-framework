/*
 * Copyright 2021 Netflix, Inc.
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

package com.netflix.graphql.dgs.autoconfig

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsRuntimeWiring
import graphql.scalars.ExtendedScalars
import graphql.schema.GraphQLScalarType
import graphql.schema.idl.RuntimeWiring
import org.springframework.boot.autoconfigure.condition.AllNestedConditions
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Conditional
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.ConfigurationCondition

@ConditionalOnClass(graphql.scalars.ExtendedScalars::class)
@ConditionalOnProperty(
    prefix = "dgs.graphql.extensions.scalars",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true
)
@Configuration(proxyBeanMethods = false)
open class DgsExtendedScalarsAutoConfiguration {

    @ConditionalOnProperty(
        prefix = "dgs.graphql.extensions.scalars.time-dates",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true
    )
    @Configuration(proxyBeanMethods = false)
    open class TimeExtendedScalarsAutoConfiguration {
        @Bean
        open fun timesExtendedScalarsRegistrar(): ExtendedScalarRegistrar {
            return object : AbstractExtendedScalarRegistrar() {
                override fun getScalars(): List<GraphQLScalarType> {
                    return listOf(
                        ExtendedScalars.DateTime,
                        ExtendedScalars.Date,
                        ExtendedScalars.Time,
                        ExtendedScalars.LocalTime
                    )
                }
            }
        }
    }

    @ConditionalOnProperty(
        prefix = "dgs.graphql.extensions.scalars.objects",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true
    )
    @Configuration(proxyBeanMethods = false)
    open class ObjectsExtendedScalarsAutoConfiguration {
        @Bean
        open fun objectsExtendedScalarsRegistrar(): ExtendedScalarRegistrar {
            return object : AbstractExtendedScalarRegistrar() {
                override fun getScalars(): List<GraphQLScalarType> {
                    return listOf(
                        ExtendedScalars.Object,
                        ExtendedScalars.Json,
                        ExtendedScalars.Url,
                        ExtendedScalars.Locale
                    )
                }
            }
        }
    }

    @ConditionalOnProperty(
        prefix = "dgs.graphql.extensions.scalars.numbers",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true
    )
    @Configuration(proxyBeanMethods = false)
    open class NumbersExtendedScalarsAutoConfiguration {
        @Bean
        open fun numbersExtendedScalarsRegistrar(): ExtendedScalarRegistrar {
            return object : AbstractExtendedScalarRegistrar() {
                override fun getScalars(): List<GraphQLScalarType> {
                    return listOf(
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
                        ExtendedScalars.GraphQLByte
                    )
                }
            }
        }

        @Conditional(OnBigDecimalAndNumbers::class)
        @Configuration(proxyBeanMethods = false)
        open class BigDecimalAutoConfiguration {
            @Bean
            open fun bigDecimalExtendedScalarsRegistrar(): ExtendedScalarRegistrar {
                return object : AbstractExtendedScalarRegistrar() {
                    override fun getScalars(): List<GraphQLScalarType> {
                        return listOf(
                            // Others
                            ExtendedScalars.GraphQLBigDecimal
                        )
                    }
                }
            }
        }

        open class OnBigDecimalAndNumbers :
            AllNestedConditions(ConfigurationCondition.ConfigurationPhase.PARSE_CONFIGURATION) {
            @ConditionalOnProperty(
                prefix = "dgs.graphql.extensions.scalars.numbers.",
                name = ["enabled"],
                havingValue = "true",
                matchIfMissing = true
            )
            open class OnNumbers

            @ConditionalOnProperty(
                prefix = "dgs.graphql.extensions.scalars.numbers.bigdecimal",
                name = ["enabled"],
                havingValue = "true",
                matchIfMissing = true
            )
            open class OnBigDecimal
        }

        @Conditional(OnBigIntegerAndNumbers::class)
        @Configuration(proxyBeanMethods = false)
        open class BigIntegerAutoConfiguration {
            @Bean
            open fun bigIntegerExtendedScalarsRegistrar(): ExtendedScalarRegistrar {
                return object : AbstractExtendedScalarRegistrar() {
                    override fun getScalars(): List<GraphQLScalarType> {
                        return listOf(ExtendedScalars.GraphQLBigInteger)
                    }
                }
            }
        }

        open class OnBigIntegerAndNumbers :
            AllNestedConditions(ConfigurationCondition.ConfigurationPhase.PARSE_CONFIGURATION) {
            @ConditionalOnProperty(
                prefix = "dgs.graphql.extensions.scalars.numbers.",
                name = ["enabled"],
                havingValue = "true",
                matchIfMissing = true
            )
            open class OnNumbers

            @ConditionalOnProperty(
                prefix = "dgs.graphql.extensions.scalars.numbers.biginteger",
                name = ["enabled"],
                havingValue = "true",
                matchIfMissing = true
            )
            open class OnBigInteger
        }
    }

    @ConditionalOnProperty(
        prefix = "dgs.graphql.extensions.scalars.chars",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true
    )
    @Configuration(proxyBeanMethods = false)
    open class CharsExtendedScalarsAutoConfiguration {
        @Bean
        open fun charsExtendedScalarsRegistrar(): ExtendedScalarRegistrar {
            return object : AbstractExtendedScalarRegistrar() {
                override fun getScalars(): List<GraphQLScalarType> {
                    return listOf(ExtendedScalars.GraphQLChar)
                }
            }
        }
    }

    @ConditionalOnProperty(
        prefix = "dgs.graphql.extensions.scalars.ids",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true
    )
    @Configuration(proxyBeanMethods = false)
    open class IDsExtendedScalarsAutoConfiguration {
        @Bean
        open fun idsExtendedScalarsRegistrar(): ExtendedScalarRegistrar {
            return object : AbstractExtendedScalarRegistrar() {
                override fun getScalars(): List<GraphQLScalarType> {
                    return listOf(ExtendedScalars.UUID)
                }
            }
        }
    }

    @DgsComponent
    @FunctionalInterface
    fun interface ExtendedScalarRegistrar {
        fun getScalars(): List<GraphQLScalarType>
    }

    abstract class AbstractExtendedScalarRegistrar : ExtendedScalarRegistrar {

        @DgsRuntimeWiring
        fun addScalar(builder: RuntimeWiring.Builder): RuntimeWiring.Builder {
            return getScalars().foldRight(builder) { a, acc -> acc.scalar(a) }
        }
    }
}
