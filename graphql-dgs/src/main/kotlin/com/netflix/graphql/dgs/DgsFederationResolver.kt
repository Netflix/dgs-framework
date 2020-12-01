package com.netflix.graphql.dgs

import graphql.schema.DataFetcher
import graphql.schema.TypeResolver

/**
 * Required only when federation is used.
 * For federation the frameworks needs a mapping from __typename to an actual type which is done by the entitiesFetcher.
 * The typeResolver takes an instance of a concrete type, and returns its type name as defined in the schema.
 * See http://manuals.test.netflix.net/view/dgs/mkdocs/master/federation/
 */
interface DgsFederationResolver {
    fun entitiesFetcher(): DataFetcher<Any?>
    fun typeResolver() : TypeResolver
}