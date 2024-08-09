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

package com.netflix.graphql.dgs

import graphql.schema.DataFetcher
import graphql.schema.TypeResolver

/**
 * Required only when federation is used.
 * For federation the frameworks needs a mapping from __typename to an actual type which is done by the entitiesFetcher.
 * The typeResolver takes an instance of a concrete type, and returns its type name as defined in the schema.
 */
interface DgsFederationResolver {
    fun entitiesFetcher(): DataFetcher<Any?>

    fun typeResolver(): TypeResolver
}
