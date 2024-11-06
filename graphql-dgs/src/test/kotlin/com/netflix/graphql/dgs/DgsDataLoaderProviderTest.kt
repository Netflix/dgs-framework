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

import com.netflix.graphql.dgs.exceptions.DgsUnnamedDataLoaderOnFieldException
import com.netflix.graphql.dgs.exceptions.InvalidDataLoaderTypeException
import com.netflix.graphql.dgs.exceptions.MultipleDataLoadersDefinedException
import com.netflix.graphql.dgs.internal.DgsDataLoaderProvider
import com.netflix.graphql.dgs.internal.DgsWrapWithContextDataLoaderCustomizer
import graphql.schema.DataFetchingEnvironmentImpl
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.dataloader.BatchLoader
import org.dataloader.BatchLoaderWithContext
import org.dataloader.DataLoaderRegistry
import org.dataloader.MappedBatchLoader
import org.dataloader.MappedBatchLoaderWithContext
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.BeanCreationException
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.lang.IllegalStateException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

class DgsDataLoaderProviderTest {
    private val applicationContextRunner: ApplicationContextRunner =
        ApplicationContextRunner()
            .withBean(DgsDataLoaderProvider::class.java)

    @Test
    fun findDataLoaders() {
        applicationContextRunner
            .withBean(
                ExampleBatchLoader::class.java,
            ).withBean(ExampleBatchLoaderWithDispatchPredicate::class.java)
            .run { context ->
                val provider = context.getBean(DgsDataLoaderProvider::class.java)
                val dataLoaderRegistry = provider.buildRegistry()
                Assertions.assertEquals(2, dataLoaderRegistry.dataLoaders.size)
                val dataLoader = dataLoaderRegistry.getDataLoader<Any, Any>("exampleLoader")
                Assertions.assertNotNull(dataLoader)
                val dataLoaderWithDispatch = dataLoaderRegistry.getDataLoader<Any, Any>("exampleLoaderWithDispatch")
                Assertions.assertNotNull(dataLoaderWithDispatch)
            }
    }

    @Test
    fun findDataLoadersWithContext() {
        applicationContextRunner
            .withBean(
                ExampleBatchLoaderWithContext::class.java,
            ).withBean(ExampleBatchLoaderWithContextAndDispatchPredicate::class.java)
            .run { context ->
                val provider = context.getBean(DgsDataLoaderProvider::class.java)
                val dataLoaderRegistry = provider.buildRegistry()
                Assertions.assertEquals(2, dataLoaderRegistry.dataLoaders.size)
                val dataLoader = dataLoaderRegistry.getDataLoader<Any, Any>("exampleLoaderWithContext")
                Assertions.assertNotNull(dataLoader)
                val dataLoaderWithDispatch = dataLoaderRegistry.getDataLoader<Any, Any>("exampleLoaderWithContextAndDispatch")
                Assertions.assertNotNull(dataLoaderWithDispatch)
            }
    }

    @Test
    fun detectDuplicateDataLoaders() {
        applicationContextRunner.withBean(ExampleBatchLoader::class.java).withBean(ExampleDuplicateBatchLoader::class.java).run { context ->
            val exc =
                assertThrows<IllegalStateException> {
                    val provider = context.getBean(DgsDataLoaderProvider::class.java)
                    provider.buildRegistry()
                }

            assertThat(exc.cause)
                .isInstanceOf(BeanCreationException::class.java)
                .rootCause()
                .isInstanceOf(MultipleDataLoadersDefinedException::class.java)
        }
    }

    @Test
    fun dataLoaderInvalidType() {
        @DgsDataLoader
        class Foo
        applicationContextRunner
            .withBean(Foo::class.java)
            .run { context ->
                val exc =
                    assertThrows<IllegalStateException> {
                        context.getBean(DgsDataLoaderProvider::class.java)
                    }
                assertThat(exc.cause)
                    .isInstanceOf(BeanCreationException::class.java)
                    .rootCause()
                    .isInstanceOf(InvalidDataLoaderTypeException::class.java)
            }
    }

    @Test
    fun findDataLoadersFromFields() {
        applicationContextRunner.withBean(ExampleBatchLoaderFromField::class.java).run { context ->
            val provider = context.getBean(DgsDataLoaderProvider::class.java)
            val dataLoaderRegistry = provider.buildRegistry()
            Assertions.assertEquals(2, dataLoaderRegistry.dataLoaders.size)
            val dataLoader = dataLoaderRegistry.getDataLoader<Any, Any>("exampleLoaderFromField")
            Assertions.assertNotNull(dataLoader)

            val privateDataLoader = dataLoaderRegistry.getDataLoader<Any, Any>("privateExampleLoaderFromField")
            Assertions.assertNotNull(privateDataLoader)
        }
    }

    @Test
    fun findMappedDataLoaders() {
        applicationContextRunner
            .withBean(
                ExampleMappedBatchLoader::class.java,
            ).withBean(ExampleMappedBatchLoaderWithDispatchPredicate::class.java)
            .run { context ->
                val provider = context.getBean(DgsDataLoaderProvider::class.java)
                val dataLoaderRegistry = provider.buildRegistry()
                Assertions.assertEquals(2, dataLoaderRegistry.dataLoaders.size)
                val dataLoader = dataLoaderRegistry.getDataLoader<Any, Any>("exampleMappedLoader")
                Assertions.assertNotNull(dataLoader)
                val dataLoaderWithDispatch = dataLoaderRegistry.getDataLoader<Any, Any>("exampleMappedLoaderWithDispatch")
                Assertions.assertNotNull(dataLoaderWithDispatch)
            }
    }

    @Test
    fun findMappedDataLoadersWithContext() {
        applicationContextRunner
            .withBean(
                ExampleMappedBatchLoaderWithContext::class.java,
            ).withBean(ExampleMappedBatchLoaderWithContextAndDispatchPredicate::class.java)
            .run { context ->
                val provider = context.getBean(DgsDataLoaderProvider::class.java)
                val dataLoaderRegistry = provider.buildRegistry()
                Assertions.assertEquals(2, dataLoaderRegistry.dataLoaders.size)
                val dataLoader = dataLoaderRegistry.getDataLoader<Any, Any>("exampleMappedLoaderWithContext")
                Assertions.assertNotNull(dataLoader)
                val dataLoaderWithDispatch = dataLoaderRegistry.getDataLoader<Any, Any>("exampleMappedLoaderWithContextAndDispatch")
                Assertions.assertNotNull(dataLoaderWithDispatch)
            }
    }

    @Test
    fun findMappedDataLoadersFromFields() {
        applicationContextRunner.withBean(ExampleMappedBatchLoaderFromField::class.java).run { context ->
            val provider = context.getBean(DgsDataLoaderProvider::class.java)
            val dataLoaderRegistry = provider.buildRegistry()
            Assertions.assertEquals(2, dataLoaderRegistry.dataLoaders.size)
            val dataLoader = dataLoaderRegistry.getDataLoader<Any, Any>("exampleMappedLoaderFromField")
            Assertions.assertNotNull(dataLoader)

            val privateDataLoader = dataLoaderRegistry.getDataLoader<Any, Any>("privateExampleMappedLoaderFromField")
            Assertions.assertNotNull(privateDataLoader)
        }
    }

    @Test
    fun dataLoaderConsumer() {
        applicationContextRunner.withBean(ExampleDataLoaderWithRegistry::class.java).run { context ->
            val provider = context.getBean(DgsDataLoaderProvider::class.java)
            val registry = provider.buildRegistry()

            // Use the dataloader's "load" method to check if the registry was set correctly, because the dataloader instance isn't itself a DgsDataLoaderRegistryConsumer
            val dataLoader = registry.getDataLoader<String, String>("withRegistry")
            val load = dataLoader.load("")
            dataLoader.dispatch()
            val loaderKeys = load.get()
            assertThat(loaderKeys).isEqualTo(registry.keys.first())
        }
    }

    @Nested
    inner class UnnamedBatchLoaderTests {
        @Test
        fun findDataLoadersWithoutName() {
            applicationContextRunner.withBean(ExampleBatchLoaderWithoutName::class.java).run { context ->
                val provider = context.getBean(DgsDataLoaderProvider::class.java)
                val dataLoaderRegistry = provider.buildRegistry()
                Assertions.assertEquals(1, dataLoaderRegistry.dataLoaders.size)
                val dataLoader =
                    dataLoaderRegistry.getDataLoader<Any, Any>("ExampleBatchLoaderWithoutName")
                Assertions.assertNotNull(dataLoader)
            }
        }

        @Test
        fun findDataLoadersWithoutNameByClass() {
            applicationContextRunner.withBean(ExampleBatchLoaderWithoutName::class.java).run { context ->
                val provider = context.getBean(DgsDataLoaderProvider::class.java)
                val dataLoaderRegistry = provider.buildRegistry()
                Assertions.assertEquals(1, dataLoaderRegistry.dataLoaders.size)
                val dataLoader =
                    DgsDataFetchingEnvironment(
                        DataFetchingEnvironmentImpl
                            .newDataFetchingEnvironment()
                            .dataLoaderRegistry(dataLoaderRegistry)
                            .build(),
                        context,
                    ).getDataLoader<Any, Any>(ExampleBatchLoaderWithoutName::class.java)
                Assertions.assertNotNull(dataLoader)
            }
        }

        @Test
        fun findDataLoadersFromFieldsWithoutName() {
            applicationContextRunner.withBean(ExampleBatchLoaderWithoutNameFromField::class.java).run { context ->
                assertThatThrownBy { context.getBean(DgsDataLoaderProvider::class.java) }
                    .rootCause()
                    .isInstanceOf(DgsUnnamedDataLoaderOnFieldException::class.java)
                    .hasMessage(
                        "Field `batchLoader` in class `com.netflix.graphql.dgs.ExampleBatchLoaderWithoutNameFromField` was annotated with @DgsDataLoader, but the data loader was not given a proper name",
                    )
            }
        }

        @Test
        fun wrapWithContextDataLoaderScanningInterceptorTest() {
            applicationContextRunner
                .withBean(
                    ExampleBatchLoaderWithoutName::class.java,
                ).withBean(DgsWrapWithContextDataLoaderCustomizer::class.java)
                .withBean(DataLoaderCustomizerCounter::class.java)
                .run { context ->
                    val provider = context.getBean(DgsDataLoaderProvider::class.java)
                    val dataLoaderRegistry = provider.buildRegistry()

                    val counter = context.getBean(DataLoaderCustomizerCounter::class.java)

                    assertThat(dataLoaderRegistry.dataLoaders.size).isEqualTo(1)

                    assertThat(counter.batchLoaderCount).isEqualTo(0)
                    assertThat(counter.batchLoaderWithContextCount).isEqualTo(1)
                    assertThat(counter.mappedBatchLoaderCount).isEqualTo(0)
                    assertThat(counter.mappedBatchLoaderWithContextCount).isEqualTo(0)
                }
        }
    }

    class DataLoaderCustomizerCounter(
        var batchLoaderCount: Int = 0,
        var batchLoaderWithContextCount: Int = 0,
        var mappedBatchLoaderCount: Int = 0,
        var mappedBatchLoaderWithContextCount: Int = 0,
    ) : DgsDataLoaderCustomizer {
        override fun provide(
            original: BatchLoader<*, *>,
            name: String,
        ): Any {
            batchLoaderCount += 1
            return original
        }

        override fun provide(
            original: BatchLoaderWithContext<*, *>,
            name: String,
        ): Any {
            batchLoaderWithContextCount += 1
            return original
        }

        override fun provide(
            original: MappedBatchLoader<*, *>,
            name: String,
        ): Any {
            mappedBatchLoaderCount += 1
            return original
        }

        override fun provide(
            original: MappedBatchLoaderWithContext<*, *>,
            name: String,
        ): Any {
            mappedBatchLoaderWithContextCount += 1
            return original
        }
    }

    @DgsDataLoader(name = "withRegistry")
    class ExampleDataLoaderWithRegistry :
        BatchLoader<String, String>,
        DgsDataLoaderRegistryConsumer {
        lateinit var registry: DataLoaderRegistry

        override fun setDataLoaderRegistry(dataLoaderRegistry: DataLoaderRegistry) {
            this.registry = dataLoaderRegistry
        }

        override fun load(keys: List<String>): CompletionStage<List<String>> = CompletableFuture.completedFuture(registry.keys.toList())
    }
}
