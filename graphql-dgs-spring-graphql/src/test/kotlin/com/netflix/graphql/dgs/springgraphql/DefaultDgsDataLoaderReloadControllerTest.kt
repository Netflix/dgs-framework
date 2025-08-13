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

package com.netflix.graphql.dgs.springgraphql

import com.netflix.graphql.dgs.internal.DefaultDgsDataLoaderReloadController
import com.netflix.graphql.dgs.internal.ReloadableDgsDataLoaderProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull

class DefaultDgsDataLoaderReloadControllerTest {
    @Test
    fun `should successfully reload data loaders`() {
        val mockProvider = mockk<ReloadableDgsDataLoaderProvider>()
        every { mockProvider.forceReload() } returns true

        val controller = DefaultDgsDataLoaderReloadController(mockProvider)

        val result = controller.reloadDataLoaders()

        assertTrue(result)
        assertNotNull(controller.getLastReloadTime())
        verify(exactly = 1) { mockProvider.forceReload() }
    }

    @Test
    fun `should handle reload failures gracefully`() {
        val mockProvider = mockk<ReloadableDgsDataLoaderProvider>()
        every { mockProvider.forceReload() } returns false

        val controller = DefaultDgsDataLoaderReloadController(mockProvider)

        val result = controller.reloadDataLoaders()

        assertFalse(result)
        // Last reload time should not be set on failure
        assertNull(controller.getLastReloadTime())
        verify(exactly = 1) { mockProvider.forceReload() }
    }

    @Test
    fun `should handle provider exceptions`() {
        val mockProvider = mockk<ReloadableDgsDataLoaderProvider>()
        every { mockProvider.forceReload() } throws RuntimeException("Test exception")

        val controller = DefaultDgsDataLoaderReloadController(mockProvider)

        val result = controller.reloadDataLoaders()

        assertFalse(result)
        assertNull(controller.getLastReloadTime())
        verify(exactly = 1) { mockProvider.forceReload() }
    }

    @Test
    fun `should always report reload as enabled`() {
        val mockProvider = mockk<ReloadableDgsDataLoaderProvider>()
        val controller = DefaultDgsDataLoaderReloadController(mockProvider)

        assertTrue(controller.isReloadEnabled())
    }

    @Test
    fun `should track reload statistics`() {
        val mockProvider = mockk<ReloadableDgsDataLoaderProvider>()
        every { mockProvider.forceReload() } returns true

        val controller = DefaultDgsDataLoaderReloadController(mockProvider)

        // Initial stats
        val initialStats = controller.getReloadStats()
        assertEquals(0L, initialStats.totalReloads)
        assertNull(initialStats.lastReloadTime)
        assertNull(initialStats.lastReloadDuration)
        assertTrue(initialStats.isEnabled)

        // Perform some reloads
        controller.reloadDataLoaders()
        Thread.sleep(1) // Ensure different timestamp
        controller.reloadDataLoaders()

        val finalStats = controller.getReloadStats()
        assertEquals(2L, finalStats.totalReloads)
        assertNotNull(finalStats.lastReloadTime)
        assertNotNull(finalStats.lastReloadDuration)
        assertTrue(finalStats.isEnabled)

        verify(exactly = 2) { mockProvider.forceReload() }
    }

    @Test
    fun `should update last reload time only on success`() {
        val mockProvider = mockk<ReloadableDgsDataLoaderProvider>()
        val controller = DefaultDgsDataLoaderReloadController(mockProvider)

        // First reload succeeds
        every { mockProvider.forceReload() } returns true
        controller.reloadDataLoaders()
        val firstReloadTime = controller.getLastReloadTime()
        assertNotNull(firstReloadTime)

        Thread.sleep(1) // Ensure different timestamp

        // Second reload fails
        every { mockProvider.forceReload() } returns false
        controller.reloadDataLoaders()
        val secondReloadTime = controller.getLastReloadTime()

        // Time should remain the same as the first successful reload
        assertEquals(firstReloadTime, secondReloadTime)

        val stats = controller.getReloadStats()
        assertEquals(1L, stats.totalReloads) // Only one successful reload
    }
}
