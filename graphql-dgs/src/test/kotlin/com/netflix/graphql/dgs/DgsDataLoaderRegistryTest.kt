/*
 * Copyright 2022 Netflix, Inc.
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

import com.netflix.graphql.dgs.internal.DgsDataLoaderRegistry
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.dataloader.BatchLoader
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory
import org.dataloader.registries.DispatchPredicate
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

class DgsDataLoaderRegistryTest {

    private val dgsDataLoaderRegistry = DgsDataLoaderRegistry()
    private val dataLoaderA = ExampleDataLoaderA()
    private val dataLoaderB = ExampleDataLoaderB()

    @MockK
    var mockDataLoaderA: DataLoader<String, String> = mockk()

    @MockK
    var mockDataLoaderB: DataLoader<String, String> = mockk()

    @Test
    fun register() {
        val newLoader = DataLoaderFactory.newDataLoader(dataLoaderA)
        dgsDataLoaderRegistry.register("exampleLoaderA", newLoader)
        assertThat(dgsDataLoaderRegistry.dataLoaders.size).isEqualTo(1)
        val registeredLoader = dgsDataLoaderRegistry.getDataLoader<String, String>("exampleLoaderA")
        assertThat(registeredLoader).isNotNull
    }

    @Test
    fun registerWithScheduledDispatch() {

        val newLoader = DataLoaderFactory.newDataLoader(dataLoaderB)
        dgsDataLoaderRegistry.registerWithDispatchPredicate(
            "exampleLoaderB", newLoader,
            DispatchPredicate.dispatchIfDepthGreaterThan(1)
        )
        assertThat(dgsDataLoaderRegistry.dataLoaders.size).isEqualTo(1)
        val registeredLoader = dgsDataLoaderRegistry.getDataLoader<String, String>("exampleLoaderB")
        assertThat(registeredLoader).isNotNull
    }

    @Test
    fun getDataLoaders() {
        val newLoaderA = DataLoaderFactory.newDataLoader(dataLoaderA)
        dgsDataLoaderRegistry.register("exampleLoaderA", newLoaderA)

        val newLoaderB = DataLoaderFactory.newDataLoader(dataLoaderB)
        dgsDataLoaderRegistry.registerWithDispatchPredicate(
            "exampleLoaderB", newLoaderB,
            DispatchPredicate.dispatchIfDepthGreaterThan(1)
        )
        assertThat(dgsDataLoaderRegistry.dataLoaders.size).isEqualTo(2)
        val registeredLoaderA = dgsDataLoaderRegistry.getDataLoader<String, String>("exampleLoaderA")
        assertThat(registeredLoaderA).isNotNull
        val registeredLoaderB = dgsDataLoaderRegistry.getDataLoader<String, String>("exampleLoaderB")
        assertThat(registeredLoaderB).isNotNull
    }

    @Test
    fun getDataLoadersAsMap() {
        val newLoaderA = DataLoaderFactory.newDataLoader(dataLoaderA)
        dgsDataLoaderRegistry.register("exampleLoaderA", newLoaderA)

        val newLoaderB = DataLoaderFactory.newDataLoader(dataLoaderB)
        dgsDataLoaderRegistry.registerWithDispatchPredicate(
            "exampleLoaderB", newLoaderB,
            DispatchPredicate.dispatchIfDepthGreaterThan(1)
        )

        assertThat(dgsDataLoaderRegistry.dataLoadersMap.size).isEqualTo(2)
        val registeredLoaderA = dgsDataLoaderRegistry.dataLoadersMap["exampleLoaderA"]
        assertThat(registeredLoaderA).isNotNull
        val registeredLoaderB = dgsDataLoaderRegistry.dataLoadersMap["exampleLoaderB"]
        assertThat(registeredLoaderB).isNotNull
    }

    @Test
    fun dispatchAll() {
        every { mockDataLoaderB.dispatchDepth() } returns 1
        every { mockDataLoaderB.dispatch() } returns CompletableFuture.completedFuture(emptyList<String>())
        every { mockDataLoaderA.dispatch() } returns CompletableFuture.completedFuture(emptyList<String>())

        dgsDataLoaderRegistry.register("exampleLoaderA", mockDataLoaderA)
        dgsDataLoaderRegistry.registerWithDispatchPredicate(
            "exampleLoaderB", mockDataLoaderB,
            DispatchPredicate.dispatchIfDepthGreaterThan(1)
        )
        dgsDataLoaderRegistry.dispatchAll()
    }

    @Test
    fun dispatchDepth() {
        every { mockDataLoaderA.dispatchDepth() } returns 2
        every { mockDataLoaderB.dispatchDepth() } returns 1

        dgsDataLoaderRegistry.register("exampleLoaderA", mockDataLoaderA)
        dgsDataLoaderRegistry.registerWithDispatchPredicate(
            "exampleLoaderB", mockDataLoaderB,
            DispatchPredicate.dispatchIfDepthGreaterThan(1)
        )
        assertThat(dgsDataLoaderRegistry.dispatchDepth()).isEqualTo(3)
    }

    @DgsDataLoader(name = "exampleLoaderA")
    class ExampleDataLoaderA : BatchLoader<String, String> {
        override fun load(keys: List<String>): CompletionStage<List<String>> {
            return CompletableFuture.completedFuture(listOf("A", "B", "C"))
        }
    }

    @DgsDataLoader(name = "exampleLoaderB")
    class ExampleDataLoaderB : BatchLoader<String, String> {
        override fun load(keys: List<String>): CompletionStage<List<String>> {
            return CompletableFuture.completedFuture(listOf("A", "B", "C"))
        }
    }
}
