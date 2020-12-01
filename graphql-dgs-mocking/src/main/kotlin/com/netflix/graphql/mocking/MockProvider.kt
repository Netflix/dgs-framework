package com.netflix.graphql.mocking

/**
 * Interface used by the Spring integration for the GraphQL mock library to Autowire mock provider implementations.
 */
interface MockProvider {
    fun provide(): Map<String, Any>
}
