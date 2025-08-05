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

import com.netflix.graphql.dgs.DataLoaderInstrumentationExtensionProvider
import com.netflix.graphql.dgs.ExampleBatchLoader
import com.netflix.graphql.dgs.ExampleBatchLoaderWithContext
import com.netflix.graphql.dgs.ExampleMappedBatchLoader
import com.netflix.graphql.dgs.ExampleMappedBatchLoaderWithContext
import org.assertj.core.api.Assertions.assertThat
import org.dataloader.BatchLoader
import org.dataloader.BatchLoaderWithContext
import org.dataloader.MappedBatchLoader
import org.dataloader.MappedBatchLoaderWithContext
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap

@SpringBootTest(classes = [MultiDataLoaderInstrumentationProvidersProviderTest.TestLocalConfiguration::class])
internal class MultiDataLoaderInstrumentationProvidersProviderTest {
    @Autowired
    lateinit var testAcc: MultiValueMap<String, String>

    @Autowired
    lateinit var dgsDataLoaderProvider: DgsDataLoaderProvider

    private val expectedAppliedOrder = listOf("head", "mid", "tail")

    @Test
    fun `Support multiple ordered DataLoaderInstrumentationExtensionProviders`() {
        val buildRegistry = dgsDataLoaderProvider.buildRegistry()

        assertThat(buildRegistry.dataLoaders).hasSize(4)

        assertThat(testAcc["exampleLoader"]).containsExactlyElementsOf(expectedAppliedOrder)
        assertThat(testAcc["exampleLoaderWithContext"]).containsExactlyElementsOf(expectedAppliedOrder)
        assertThat(testAcc["exampleMappedLoader"]).containsExactlyElementsOf(expectedAppliedOrder)
        assertThat(testAcc["exampleMappedLoaderWithContext"]).containsExactlyElementsOf(expectedAppliedOrder)
    }

    @Order(100)
    class HeadDataLoaderInstrumentationExtensionProvider(
        acc: MultiValueMap<String, String>,
    ) : BaseDataLoaderInstrumentationExtensionProvider(acc, "head")

    @Order(200)
    class MidDataLoaderInstrumentationExtensionProvider(
        acc: MultiValueMap<String, String>,
    ) : BaseDataLoaderInstrumentationExtensionProvider(acc, "mid")

    @Order(300)
    class TailDataLoaderInstrumentationExtensionProvider(
        acc: MultiValueMap<String, String>,
    ) : BaseDataLoaderInstrumentationExtensionProvider(acc, "tail")

    @Configuration(proxyBeanMethods = false)
    open class TestLocalConfiguration {
        @Bean
        open fun dgsDataLoaderProvider(
            applicationContext: ApplicationContext,
            extensionProviders: List<DataLoaderInstrumentationExtensionProvider>,
        ): DgsDataLoaderProvider =
            DefaultDgsDataLoaderProvider(
                applicationContext = applicationContext,
                extensionProviders = extensionProviders,
            )

        @Bean
        open fun testAcc(): MultiValueMap<String, String> = LinkedMultiValueMap()

        @Bean
        open fun exampleBatchLoader(): ExampleBatchLoader = ExampleBatchLoader()

        @Bean
        open fun exampleBatchLoaderWithContext(): ExampleBatchLoaderWithContext = ExampleBatchLoaderWithContext()

        @Bean
        open fun exampleMappedBatchLoader(): ExampleMappedBatchLoader = ExampleMappedBatchLoader()

        @Bean
        open fun exampleMappedBatchLoaderWithContext(): ExampleMappedBatchLoaderWithContext = ExampleMappedBatchLoaderWithContext()

        @Bean
        open fun headDataLoaderInstrumentationExtensionProvider(
            acc: MultiValueMap<String, String>,
        ): DataLoaderInstrumentationExtensionProvider = HeadDataLoaderInstrumentationExtensionProvider(acc)

        @Bean
        open fun midDataLoaderInstrumentationExtensionProvider(
            acc: MultiValueMap<String, String>,
        ): DataLoaderInstrumentationExtensionProvider = MidDataLoaderInstrumentationExtensionProvider(acc)

        @Bean
        open fun tailDataLoaderInstrumentationExtensionProvider(
            acc: MultiValueMap<String, String>,
        ): DataLoaderInstrumentationExtensionProvider = TailDataLoaderInstrumentationExtensionProvider(acc)
    }

    abstract class BaseDataLoaderInstrumentationExtensionProvider(
        private val acc: MultiValueMap<String, String>,
        private val value: String,
    ) : DataLoaderInstrumentationExtensionProvider {
        override fun provide(
            original: BatchLoader<*, *>,
            name: String,
        ): BatchLoader<*, *> {
            acc.add(name, value)
            return original
        }

        override fun provide(
            original: BatchLoaderWithContext<*, *>,
            name: String,
        ): BatchLoaderWithContext<*, *> {
            acc.add(name, value)
            return original
        }

        override fun provide(
            original: MappedBatchLoader<*, *>,
            name: String,
        ): MappedBatchLoader<*, *> {
            acc.add(name, value)
            return original
        }

        override fun provide(
            original: MappedBatchLoaderWithContext<*, *>,
            name: String,
        ): MappedBatchLoaderWithContext<*, *> {
            acc.add(name, value)
            return original
        }
    }
}
