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

package com.netflix.graphql.dgs.client.scalar

import graphql.GraphQLContext
import graphql.execution.CoercedVariables
import graphql.language.StringValue
import graphql.language.Value
import graphql.schema.Coercing
import graphql.schema.CoercingParseLiteralException
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class DateRangeScalar : Coercing<DateRange, String> {
    private var formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy")

    @Throws(CoercingSerializeException::class)
    override fun serialize(
        dataFetcherResult: Any,
        graphQLContext: GraphQLContext,
        locale: Locale,
    ): String {
        val range: DateRange = dataFetcherResult as DateRange
        return range.from.format(formatter) + "-" + range.to.format(formatter)
    }

    @Throws(CoercingParseValueException::class)
    override fun parseValue(
        input: Any,
        graphQLContext: GraphQLContext,
        locale: Locale,
    ): DateRange {
        val split = (input as String).split("-").toTypedArray()
        val from = LocalDate.parse(split[0], formatter)
        val to = LocalDate.parse(split[1], formatter)
        return DateRange(from, to)
    }

    @Throws(CoercingParseLiteralException::class)
    override fun parseLiteral(
        input: Value<*>,
        variables: CoercedVariables,
        graphQLContext: GraphQLContext,
        locale: Locale,
    ): DateRange {
        val split = (input as StringValue).value!!.split("-").toTypedArray()
        val from = LocalDate.parse(split[0], formatter)
        val to = LocalDate.parse(split[1], formatter)
        return DateRange(from, to)
    }
}

class DateRange(
    val from: LocalDate,
    val to: LocalDate,
)
