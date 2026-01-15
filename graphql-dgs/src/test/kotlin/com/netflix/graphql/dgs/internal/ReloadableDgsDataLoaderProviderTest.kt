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

package com.netflix.graphql.dgs.internal

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsDataLoader
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.dataloader.BatchLoader
import org.dataloader.BatchLoaderContextProvider
import org.dataloader.BatchLoaderWithContext
import org.dataloader.DataLoaderRegistry
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.ApplicationContext
import org.springframework.context.support.GenericApplicationContext
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.function.Supplier

class ReloadableDgsDataLoaderProviderTest {
    private val applicationContextRunner = ApplicationContextRunner()

    @Test
    fun `should create a data loader registry`() {
        val now = Instant.now()
        primaryAppContextRunner().run { context ->
            val provider =
                ReloadableDgsDataLoaderProvider(
                    applicationContext = context,
                    scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(),
                )

            // provider is initialized when the registry is built.
            assertThat(provider.isInitialized()).isFalse
            assertRegistry(provider.buildRegistry())
            assertThat(provider.isInitialized()).isTrue
            assertThat(provider.getLastReloadTime()).isAfter(now)
        }
    }

    @Test
    fun `should not trigger a reload if it was not requested`() {
        primaryAppContextRunner().run { context ->
            val startMarker = Instant.now()

            val provider =
                ReloadableDgsDataLoaderProvider(
                    applicationContext = context,
                    scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(),
                )
            // First call - should initialize
            assertRegistry(provider.buildRegistry())
            assertThat(provider.getLastReloadTime()).isAfter(startMarker)

            // Second call - should trigger reload
            val reloadMarker = provider.getLastReloadTime()
            assertRegistry(provider.buildRegistry())
            assertThat(provider.getLastReloadTime()).isEqualTo(reloadMarker)
        }
    }

    @Test
    fun `should be able to force a reload programmatically`() {
        primaryAppContextRunner().run { context ->
            val provider =
                ReloadableDgsDataLoaderProvider(
                    applicationContext = context,
                    scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(),
                )

            // Initialize provider
            assertRegistry(provider.buildRegistry())
            val firstReloadTime = provider.getLastReloadTime()
            assertThat(firstReloadTime).isNotNull

            // Force reload
            Thread.sleep(10) // Ensure different timestamp
            val reloadResult = provider.forceReload()
            assertThat(reloadResult).isTrue

            val secondReloadTime = provider.getLastReloadTime()
            assertThat(secondReloadTime).isNotNull.isAfter(firstReloadTime)
            assertRegistry(provider.buildRegistry())
        }
    }

    @Test
    fun `should handle force reload exceptions`() {
        primaryAppContextRunner().run { context ->
            val provider =
                ReloadableDgsDataLoaderProvider(
                    applicationContext = context,
                    scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(),
                )
            assertRegistry(provider.buildRegistry())
            assertThat(provider.isInitialized()).isTrue
            // Force a reload with an invalid context to trigger exception during reload
            assertThat(provider.forceReload(mockk())).isFalse
            // The registry is still usable.
            assertRegistry(provider.buildRegistry())
            assertThat(provider.isInitialized()).isTrue
        }
    }

    @Test
    fun `should be thread safe during reload`() {
        primaryAppContextRunner().run { context ->
            val provider =
                ReloadableDgsDataLoaderProvider(
                    applicationContext = context,
                    scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(),
                )

            // Test concurrent access
            val threads =
                (1..10).map { threadNum ->
                    Thread {
                        repeat(5) {
                            assertRegistry(provider.buildRegistry())
                        }
                    }
                }

            threads.forEach { it.start() }
            threads.forEach { it.join() }

            assertThat(provider.isInitialized()).isTrue
            assertRegistry(provider.buildRegistry())
        }
    }

    @Test
    fun `should be able to pass a context supplier to the underlying provider`() {
        primaryAppContextRunner().run { context ->
            val provider =
                ReloadableDgsDataLoaderProvider(
                    applicationContext = context,
                    scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(),
                )

            val contextSupplier = mockk<Supplier<BatchLoaderContextProvider>>()
            every { contextSupplier.get() } returns mockk<BatchLoaderContextProvider>()

            val registry = provider.buildRegistryWithContextSupplier(contextSupplier)
            assertRegistry(registry)
        }
    }

    @Test
    fun `should handle force reload with a new application context`() {
        primaryAppContextRunner().run { context ->
            val provider =
                ReloadableDgsDataLoaderProvider(
                    applicationContext = context,
                    scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(),
                )
            assertRegistry(provider.buildRegistry())
            assertThat(provider.isInitialized()).isTrue
            // Force a reload with an invalid context to trigger exception during reload
            val secondaryContext = createSecondaryAppContex(context)
            assertThat(provider.forceReload(secondaryContext)).isTrue
            // The registry is still usable.
            assertRegistry(provider.buildRegistry(), "testLoaderV2", "testLoaderWithContextV2")
        }
    }

    fun primaryAppContextRunner(): ApplicationContextRunner = applicationContextRunner.withBean(TestDataLoader::class.java)

    fun createSecondaryAppContex(parent: ApplicationContext): ApplicationContext {
        val childAppContext = GenericApplicationContext(parent)
        childAppContext.registerBean(TestDataLoaderV2::class.java, *emptyArray<Any>())
        childAppContext.refresh()
        childAppContext.start()
        return childAppContext
    }

    fun assertRegistry(
        registry: DataLoaderRegistry,
        vararg names: String = arrayOf("testLoader", "testLoaderWithContext"),
    ): DataLoaderRegistry {
        assertThat(registry).isNotNull
        assertThat(registry.dataLoaders)
            .hasSize(names.size)
            .map<String> { it.name }
            .containsExactlyInAnyOrderElementsOf(names.asList())
        return registry
    }

    // ----
    // The DataLoaders that we are going to use to assert the provider are below.
    // The TestDataLoader will be used by default
    // The TestDataLoaderV2 will only be registered in the custom application context.

    @DgsComponent
    open class TestDataLoader {
        @DgsDataLoader(name = "testLoader")
        val testLoader: BatchLoader<String, String> =
            BatchLoader { keys ->
                CompletableFuture.completedFuture(
                    keys.map { it.uppercase() },
                )
            }

        @DgsDataLoader(name = "testLoaderWithContext")
        val testLoaderWithContext: BatchLoaderWithContext<String, String> =
            BatchLoaderWithContext { keys, ble ->
                CompletableFuture.completedFuture(
                    keys.map { it.uppercase() },
                )
            }
    }

    @DgsComponent
    open class TestDataLoaderV2 {
        @DgsDataLoader(name = "testLoaderV2")
        val testLoaderV2: BatchLoader<String, String> =
            BatchLoader { keys ->
                CompletableFuture.completedFuture(
                    keys.map { it.uppercase() },
                )
            }

        @DgsDataLoader(name = "testLoaderWithContextV2")
        val testLoaderWithContextV2: BatchLoaderWithContext<String, String> =
            BatchLoaderWithContext { keys, ble ->
                CompletableFuture.completedFuture(
                    keys.map { it.uppercase() },
                )
            }
    }
}
