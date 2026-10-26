package com.netflix.graphql.dgs.metrics.micrometer.dataloader

import com.netflix.graphql.dgs.metrics.micrometer.DgsMeterRegistrySupplier
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.dataloader.BatchLoader
import org.dataloader.BatchLoaderEnvironment
import org.dataloader.BatchLoaderWithContext
import org.dataloader.MappedBatchLoader
import org.dataloader.MappedBatchLoaderWithContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.concurrent.CompletableFuture
import java.util.stream.Stream

class BatchLoaderObjectMethodsTest {
    class LoaderCase(
        val name: String,
        val original: Any,
        val proxy: Any,
        val registry: SimpleMeterRegistry,
    ) {
        override fun toString() = name
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("loaders")
    fun `toString can be used for diagnostic logging`(case: LoaderCase) {
        assertEquals(case.original.toString(), case.proxy.toString())
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("loaders")
    fun `hashCode is the identity hash code`(case: LoaderCase) {
        assertEquals(System.identityHashCode(case.proxy), case.proxy.hashCode())
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("loaders")
    fun `equals is reflexive`(case: LoaderCase) {
        assertTrue(case.proxy.equals(case.proxy))
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("loaders")
    fun `equals rejects a different object and null`(case: LoaderCase) {
        assertFalse(case.proxy.equals(Any()))
        assertFalse(case.proxy.equals(null))
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("loaders")
    fun `proxy can be stored in a hash set`(case: LoaderCase) {
        assertTrue(hashSetOf(case.proxy).contains(case.proxy))
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("loaders")
    @Suppress("UNCHECKED_CAST")
    fun `batch loading and its timer still work`(case: LoaderCase) {
        val environment = BatchLoaderEnvironment.newBatchLoaderEnvironment().build()
        val result =
            when (val loader = case.proxy) {
                is BatchLoader<*, *> -> (loader as BatchLoader<Int, String>).load(listOf(1))
                is BatchLoaderWithContext<*, *> -> (loader as BatchLoaderWithContext<Int, String>).load(listOf(1), environment)
                is MappedBatchLoader<*, *> -> (loader as MappedBatchLoader<Int, String>).load(setOf(1))
                is MappedBatchLoaderWithContext<*, *> -> (loader as MappedBatchLoaderWithContext<Int, String>).load(setOf(1), environment)
                else -> error("Unexpected loader")
            }.toCompletableFuture().get()
        if (result is Map<*, *>) assertEquals(mapOf(1 to "value-1"), result) else assertEquals(listOf("value-1"), result)
        assertEquals(
            1L,
            case.registry
                .get("gql.dataLoader")
                .tag("gql.loaderName", case.name)
                .timer()
                .count(),
        )
    }

    companion object {
        @JvmStatic
        fun loaders(): Stream<LoaderCase> {
            val registry = SimpleMeterRegistry()
            val provider = DgsDataLoaderInstrumentationProvider(DgsMeterRegistrySupplier { registry })
            val batch = BatchLoader<Int, String> { keys -> CompletableFuture.completedFuture(keys.map { "value-$it" }) }
            val contextual = BatchLoaderWithContext<Int, String> { keys, _ -> CompletableFuture.completedFuture(keys.map { "value-$it" }) }
            val mapped = MappedBatchLoader<Int, String> { keys -> CompletableFuture.completedFuture(keys.associateWith { "value-$it" }) }
            val mappedContext =
                MappedBatchLoaderWithContext<Int, String> {
                    keys,
                    _,
                    ->
                    CompletableFuture.completedFuture(keys.associateWith { "value-$it" })
                }
            return Stream.of(
                LoaderCase("batch", batch, provider.provide(batch, "batch"), registry),
                LoaderCase("context", contextual, provider.provide(contextual, "context"), registry),
                LoaderCase("mapped", mapped, provider.provide(mapped, "mapped"), registry),
                LoaderCase("mapped-context", mappedContext, provider.provide(mappedContext, "mapped-context"), registry),
            )
        }
    }
}
