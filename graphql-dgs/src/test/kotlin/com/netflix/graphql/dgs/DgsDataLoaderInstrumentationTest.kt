/*
 * Copyright 2024 Netflix, Inc.
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

import com.netflix.graphql.dgs.internal.DgsDataLoaderProvider
import com.netflix.graphql.dgs.internal.DgsSimpleDataLoaderInstrumentationDataLoaderCustomizer
import com.netflix.graphql.dgs.internal.DgsWrapWithContextDataLoaderCustomizer
import org.assertj.core.api.Assertions.assertThat
import org.dataloader.BatchLoaderEnvironment
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.util.concurrent.atomic.AtomicInteger

/*
 * Copyright 2024 Netflix, Inc.
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

class DgsDataLoaderInstrumentationTest {
    private val applicationContextRunner: ApplicationContextRunner = ApplicationContextRunner()
        .withBean(DgsDataLoaderProvider::class.java)

    @Test
    fun instrumentationIsCorrectlyCalled() {
        val beforeCounter = AtomicInteger(0)
        val afterCounter = AtomicInteger(0)
        val exceptionCounter = AtomicInteger(0)

        applicationContextRunner.withBean(ExampleBatchLoaderWithoutName::class.java)
            .withBean(DgsWrapWithContextDataLoaderCustomizer::class.java)
            .withBean(DgsSimpleDataLoaderInstrumentationDataLoaderCustomizer::class.java)
            .withBean(TestDataLoaderInstrumentation::class.java, beforeCounter, afterCounter, exceptionCounter).run { context ->
                val provider = context.getBean(DgsDataLoaderProvider::class.java)
                val dataLoaderRegistry = provider.buildRegistry()

                val dataLoader = dataLoaderRegistry.getDataLoader<Any, Any>("ExampleBatchLoaderWithoutName")
                dataLoader.dispatch().whenComplete { _, _ ->
                    assertThat(beforeCounter.get()).isEqualTo(1)
                    assertThat(afterCounter.get()).isEqualTo(1)
                    assertThat(exceptionCounter.get()).isEqualTo(0)
                }
            }
    }

    @Test
    fun canDetectWhenDataLoaderThrows() {
        val beforeCounter = AtomicInteger(0)
        val afterCounter = AtomicInteger(0)
        val exceptionCounter = AtomicInteger(0)

        applicationContextRunner.withBean(ExampleBatchLoaderThatThrows::class.java)
            .withBean(DgsWrapWithContextDataLoaderCustomizer::class.java)
            .withBean(DgsSimpleDataLoaderInstrumentationDataLoaderCustomizer::class.java)
            .withBean(TestDataLoaderInstrumentation::class.java, beforeCounter, afterCounter, exceptionCounter).run { context ->
                val provider = context.getBean(DgsDataLoaderProvider::class.java)
                val dataLoaderRegistry = provider.buildRegistry()

                val dataLoader = dataLoaderRegistry.getDataLoader<Any, Any>("exampleLoaderThatThrows")
                dataLoader.dispatch().whenComplete { _, _ ->
                    assertThat(beforeCounter.get()).isEqualTo(1)
                    assertThat(afterCounter.get()).isEqualTo(0)
                    assertThat(exceptionCounter.get()).isEqualTo(1)
                }
            }
    }
}

class TestDataLoaderInstrumentation(private val beforeCounter: AtomicInteger, private val afterCounter: AtomicInteger, private val exceptionCounter: AtomicInteger) : DgsDataLoaderInstrumentation {
    class TestDataLoaderInstrumentationContext(private val afterCounter: AtomicInteger, private val exceptionCounter: AtomicInteger) : DgsDataLoaderInstrumentationContext {
        override fun onComplete(result: Any?, exception: Any?) {
            if (result != null) {
                afterCounter.addAndGet(1)
            }

            if (exception != null) {
                exceptionCounter.addAndGet(1)
            }
        }
    }

    override fun onDispatch(name: String, keys: List<Any>, batchLoaderEnvironment: BatchLoaderEnvironment): TestDataLoaderInstrumentationContext {
        beforeCounter.addAndGet(1)
        return TestDataLoaderInstrumentationContext(afterCounter, exceptionCounter)
    }
}
