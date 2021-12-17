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

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.boot.context.properties.NestedConfigurationProperty
import org.springframework.boot.context.properties.bind.DefaultValue
import org.springframework.lang.Nullable

// import javax.annotation.PostConstruct

@ConstructorBinding
@ConfigurationProperties(prefix = "dgs.graphql.datetime.scalars")
@Suppress("ConfigurationProperties")
class DgsDatetimeScalarsConfigurationProperties(
    @DefaultValue("false") var zoneConversionEnabled: Boolean = false,
    @NestedConfigurationProperty var date: DgsDatetimeScalarsDateConfigurationProperties = DgsDatetimeScalarsDateConfigurationProperties(),
    @NestedConfigurationProperty var localDate: DgsDatetimeScalarsLocalDateConfigurationProperties = DgsDatetimeScalarsLocalDateConfigurationProperties(),
    @NestedConfigurationProperty var localDateTime: DgsDatetimeScalarsLocalDateTimeConfigurationProperties = DgsDatetimeScalarsLocalDateTimeConfigurationProperties(),
    @NestedConfigurationProperty var localTime: DgsDatetimeScalarsLocalTimeConfigurationProperties = DgsDatetimeScalarsLocalTimeConfigurationProperties(),
    @NestedConfigurationProperty var offsetDateTime: DgsDatetimeScalarsOffsetDateTimeConfigurationProperties = DgsDatetimeScalarsOffsetDateTimeConfigurationProperties(),
    @NestedConfigurationProperty var yearMonth: DgsDatetimeScalarsYearMonthConfigurationProperties = DgsDatetimeScalarsYearMonthConfigurationProperties(),
    @NestedConfigurationProperty var duration: DgsDatetimeScalarsDurationConfigurationProperties = DgsDatetimeScalarsDurationConfigurationProperties()
) {

    data class DgsDatetimeScalarsDateConfigurationProperties(
        @DefaultValue("Date") var scalarName: String = "Date"
    )

    data class DgsDatetimeScalarsLocalDateConfigurationProperties(
        @DefaultValue("LocalDate") var scalarName: String = "LocalDate",
        @Nullable var format: String? = null
    )

    data class DgsDatetimeScalarsLocalDateTimeConfigurationProperties(
        @DefaultValue("LocalDateTime") var scalarName: String = "LocalDateTime",
        @Nullable var format: String? = null
    )

    data class DgsDatetimeScalarsLocalTimeConfigurationProperties(
        @DefaultValue("LocalTime") var scalarName: String = "LocalTime"
    )

    data class DgsDatetimeScalarsOffsetDateTimeConfigurationProperties(
        @DefaultValue("OffsetDateTime") var scalarName: String = "OffsetDateTime"
    )

    data class DgsDatetimeScalarsYearMonthConfigurationProperties(
        @DefaultValue("YearMonth") var scalarName: String = "YearMonth"
    )

    data class DgsDatetimeScalarsDurationConfigurationProperties(
        @DefaultValue("Duration") var scalarName: String = "Duration"
    )

    /* @PostConstruct
    fun validatePaths() {
        validatePath(this.date.scalarName, "dgs.graphql.datetime.scalars.date.scalarName")
    }

    private fun validatePath(path: String, pathProperty: String) {
        if (path != "/" && (!path.startsWith("/") || path.endsWith("/"))) {
            throw IllegalArgumentException("$pathProperty must start with '/' and not end with '/' but was '$path'")
        }
    }*/
}
