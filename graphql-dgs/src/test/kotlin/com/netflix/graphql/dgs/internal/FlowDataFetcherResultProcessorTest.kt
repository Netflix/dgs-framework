/*
 * Copyright 2026 Netflix, Inc.
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

import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
import graphql.language.OperationDefinition
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flow
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class FlowDataFetcherResultProcessorTest {
    @Test
    fun `query flow is collected away from the caller thread`() {
        val dfe = mockk<DgsDataFetchingEnvironment>()
        every { dfe.operationDefinition } returns
            OperationDefinition.newOperationDefinition()
                .operation(OperationDefinition.Operation.QUERY)
                .build()

        val callerThread = Thread.currentThread()
        val collectorThread = AtomicReference<Thread>()
        val callerReturnedFromProcess = CountDownLatch(1)
        val collectorObservedCallerReturn = AtomicBoolean(false)
        val result =
            FlowDataFetcherResultProcessor().process(
                flow {
                    collectorThread.set(Thread.currentThread())
                    collectorObservedCallerReturn.set(callerReturnedFromProcess.await(2, TimeUnit.SECONDS))
                    emit("result")
                },
                dfe,
            ) as CompletableFuture<*>
        callerReturnedFromProcess.countDown()

        assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(listOf("result"))
        assertThat(collectorObservedCallerReturn.get()).isTrue()
        assertThat(collectorThread.get()).isNotSameAs(callerThread)
    }
}
