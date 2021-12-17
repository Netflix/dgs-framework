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

import DgsDatetimeScalarsConfigurationProperties
import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsRuntimeWiring
import com.zhokhov.graphql.datetime.DateScalar
import com.zhokhov.graphql.datetime.DurationScalar
import com.zhokhov.graphql.datetime.LocalDateScalar
import com.zhokhov.graphql.datetime.LocalDateTimeScalar
import com.zhokhov.graphql.datetime.LocalTimeScalar
import com.zhokhov.graphql.datetime.OffsetDateTimeScalar
import com.zhokhov.graphql.datetime.YearMonthScalar
import graphql.schema.GraphQLScalarType
import graphql.schema.idl.RuntimeWiring
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.format.DateTimeFormatter

@ConditionalOnProperty(
    prefix = "dgs.graphql.datetime.scalars",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true
)
@Configuration(proxyBeanMethods = false)
open class DgsDatetimeScalarsAutoConfiguration() {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(DgsDatetimeScalarsConfigurationProperties::class)
    open class DateTimeScalarsAutoConfiguration(val configProps: DgsDatetimeScalarsConfigurationProperties) {
        @Bean
        open fun dateTimeScalarsRegistrar(): ExtendedScalarRegistrar {
            return object : AbstractExtendedScalarRegistrar() {
                override fun getScalars(): List<GraphQLScalarType> {

                    val localDateFormatter = if (configProps.localDate.format != null) DateTimeFormatter.ofPattern(configProps.localDate.format) else null
                    val localDateTimeFormatter = if (configProps.localDateTime.format != null) DateTimeFormatter.ofPattern(configProps.localDateTime.format) else null

                    return listOf(
                        DateScalar.create(configProps.date.scalarName),
                        LocalDateScalar.create(configProps.localDate.scalarName, configProps.zoneConversionEnabled, localDateFormatter),
                        LocalDateTimeScalar.create(configProps.localDateTime.scalarName, configProps.zoneConversionEnabled, localDateTimeFormatter),
                        LocalTimeScalar.create(configProps.localTime.scalarName),
                        OffsetDateTimeScalar.create(configProps.offsetDateTime.scalarName),
                        YearMonthScalar.create(configProps.yearMonth.scalarName),
                        DurationScalar.create(configProps.duration.scalarName)
                    )
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
