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
import com.netflix.graphql.dgs.internal.DgsDataLoaderProvider
import graphql.schema.DataFetchingEnvironmentImpl
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.dataloader.BatchLoader
import org.dataloader.DataLoaderRegistry
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.support.StaticListableBeanFactory
import org.springframework.context.ApplicationContext
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

@ExtendWith(MockKExtension::class)
class DgsDataLoaderProviderTest {
    @MockK
    lateinit var applicationContextMock: ApplicationContext

    @BeforeEach
    fun setDataLoaderInstrumentationExtensionProvider() {
        val listableBeanFactory = StaticListableBeanFactory()
        every { applicationContextMock.getBeanProvider(DataLoaderInstrumentationExtensionProvider::class.java) } returns
            listableBeanFactory.getBeanProvider(DataLoaderInstrumentationExtensionProvider::class.java)
    }

    @Test
    fun findDataLoaders() {
        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDataLoader::class.java) } returns mapOf(
            Pair(
                "helloFetcher",
                ExampleBatchLoader()
            )
        )

        val provider = DgsDataLoaderProvider(applicationContextMock)
        provider.findDataLoaders()
        val dataLoaderRegistry = provider.buildRegistry()
        Assertions.assertEquals(1, dataLoaderRegistry.dataLoaders.size)
        val dataLoader = dataLoaderRegistry.getDataLoader<Any, Any>("exampleLoader")
        Assertions.assertNotNull(dataLoader)
    }

    @Test
    fun dataLoaderInvalidType() {
        every { applicationContextMock.getBeansWithAnnotation(DgsDataLoader::class.java) } returns mapOf(
            Pair(
                "helloFetcher",
                object {}
            )
        )
        val provider = DgsDataLoaderProvider(applicationContextMock)
        assertThrows<InvalidDataLoaderTypeException> { provider.findDataLoaders() }
    }

    @Test
    fun findDataLoadersFromFields() {
        every { applicationContextMock.getBeansWithAnnotation(DgsDataLoader::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(
            Pair(
                "helloFetcher",
                ExampleBatchLoaderFromField()
            )
        )

        val provider = DgsDataLoaderProvider(applicationContextMock)
        provider.findDataLoaders()
        val dataLoaderRegistry = provider.buildRegistry()
        Assertions.assertEquals(2, dataLoaderRegistry.dataLoaders.size)
        val dataLoader = dataLoaderRegistry.getDataLoader<Any, Any>("exampleLoaderFromField")
        Assertions.assertNotNull(dataLoader)

        val privateDataLoader = dataLoaderRegistry.getDataLoader<Any, Any>("privateExampleLoaderFromField")
        Assertions.assertNotNull(privateDataLoader)
    }

    @Test
    fun findMappedDataLoaders() {
        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDataLoader::class.java) } returns mapOf(
            Pair(
                "helloFetcher",
                ExampleMappedBatchLoader()
            )
        )

        val provider = DgsDataLoaderProvider(applicationContextMock)
        provider.findDataLoaders()
        val dataLoaderRegistry = provider.buildRegistry()
        Assertions.assertEquals(1, dataLoaderRegistry.dataLoaders.size)
        val dataLoader = dataLoaderRegistry.getDataLoader<Any, Any>("exampleMappedLoader")
        Assertions.assertNotNull(dataLoader)
    }

    @Test
    fun findMappedDataLoadersFromFields() {
        every { applicationContextMock.getBeansWithAnnotation(DgsDataLoader::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(
            Pair(
                "helloFetcher",
                ExampleMappedBatchLoaderFromField()
            )
        )

        val provider = DgsDataLoaderProvider(applicationContextMock)
        provider.findDataLoaders()
        val dataLoaderRegistry = provider.buildRegistry()
        Assertions.assertEquals(2, dataLoaderRegistry.dataLoaders.size)
        val dataLoader = dataLoaderRegistry.getDataLoader<Any, Any>("exampleMappedLoaderFromField")
        Assertions.assertNotNull(dataLoader)

        val privateDataLoader = dataLoaderRegistry.getDataLoader<Any, Any>("privateExampleMappedLoaderFromField")
        Assertions.assertNotNull(privateDataLoader)
    }

    @Test
    fun dataLoaderConsumer() {
        every { applicationContextMock.getBeansWithAnnotation(DgsDataLoader::class.java) } returns mapOf("withRegistry" to ExampleDataLoaderWithRegistry())
        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns emptyMap()

        val provider = DgsDataLoaderProvider(applicationContextMock)
        provider.findDataLoaders()
        val registry = provider.buildRegistry()

        // Use the dataloader's "load" method to check if the registry was set correctly, because the dataloader instance isn't itself a DgsDataLoaderRegistryConsumer
        val dataLoader = registry.getDataLoader<String, String>("withRegistry")
        val load = dataLoader.load("")
        dataLoader.dispatch()
        val loaderKeys = load.get()
        assertThat(loaderKeys).isEqualTo(registry.keys.toMutableList()[0])
    }

    @Nested
    inner class UnnamedBatchLoaderTests {
        @Test
        fun findDataLoadersWithoutName() {
            every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns emptyMap()
            every { applicationContextMock.getBeansWithAnnotation(DgsDataLoader::class.java) } returns mapOf(
                Pair(
                    "helloFetcher",
                    ExampleBatchLoaderWithoutName()
                )
            )

            val provider = DgsDataLoaderProvider(applicationContextMock)
            provider.findDataLoaders()
            val dataLoaderRegistry = provider.buildRegistry()
            Assertions.assertEquals(1, dataLoaderRegistry.dataLoaders.size)
            val dataLoader =
                dataLoaderRegistry.getDataLoader<Any, Any>("ExampleBatchLoaderWithoutName")
            Assertions.assertNotNull(dataLoader)
        }

        @Test
        fun findDataLoadersWithoutNameByClass() {
            every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns emptyMap()
            val theLoader = ExampleBatchLoaderWithoutName()
            Assertions.assertEquals(
                DgsDataLoader.GENERATE_DATA_LOADER_NAME,
                theLoader.javaClass.getAnnotation(DgsDataLoader::class.java).name
            )
            every {
                applicationContextMock.getBeansWithAnnotation(DgsDataLoader::class.java)
            } returns mapOf(Pair("helloFetcher", theLoader))

            val provider = DgsDataLoaderProvider(applicationContextMock)
            provider.findDataLoaders()
            val dataLoaderRegistry = provider.buildRegistry()
            Assertions.assertEquals(1, dataLoaderRegistry.dataLoaders.size)
            val dataLoader = DgsDataFetchingEnvironment(
                DataFetchingEnvironmentImpl.newDataFetchingEnvironment()
                    .dataLoaderRegistry(dataLoaderRegistry).build()
            )
                .getDataLoader<Any, Any>(ExampleBatchLoaderWithoutName::class.java)
            Assertions.assertNotNull(dataLoader)
        }

        @Test
        fun findDataLoadersFromFieldsWithoutName() {
            every { applicationContextMock.getBeansWithAnnotation(DgsDataLoader::class.java) } returns emptyMap()
            every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(
                Pair(
                    "helloFetcher",
                    ExampleBatchLoaderWithoutNameFromField()
                )
            )

            val provider = DgsDataLoaderProvider(applicationContextMock)
            assertThatThrownBy { provider.findDataLoaders() }
                .hasNoCause()
                .isExactlyInstanceOf(DgsUnnamedDataLoaderOnFieldException::class.java)
                .hasMessage(
                    "Field `batchLoader` in class `com.netflix.graphql.dgs.ExampleBatchLoaderWithoutNameFromField` was annotated with @DgsDataLoader, but the data loader was not given a proper name"
                )
        }
    }

    @DgsDataLoader(name = "withRegistry")
    class ExampleDataLoaderWithRegistry : BatchLoader<String, String>, DgsDataLoaderRegistryConsumer {

        lateinit var registry: DataLoaderRegistry

        override fun setDataLoaderRegistry(dataLoaderRegistry: DataLoaderRegistry) {
            this.registry = dataLoaderRegistry
        }

        override fun load(keys: List<String>): CompletionStage<MutableList<String>>? {
            return CompletableFuture.completedFuture(registry.keys.toMutableList())
        }
    }
}
