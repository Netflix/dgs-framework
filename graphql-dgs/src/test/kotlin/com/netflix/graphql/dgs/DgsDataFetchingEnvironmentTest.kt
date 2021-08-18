/*
 * Copyright 2021 Netflix, Inc.
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
import com.netflix.graphql.dgs.internal.DgsSchemaProvider
import graphql.ExecutionInput
import graphql.GraphQL
import graphql.schema.DataFetchingEnvironment
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.dataloader.DataLoader
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.support.StaticListableBeanFactory
import org.springframework.context.ApplicationContext
import java.util.*
import java.util.concurrent.CompletableFuture

@ExtendWith(MockKExtension::class)
internal class DgsDataFetchingEnvironmentTest {
    @MockK
    lateinit var applicationContextMock: ApplicationContext

    @RelaxedMockK
    lateinit var dfeMock: DataFetchingEnvironment

    val helloFetcher = object : Any() {
        @DgsData(parentType = "Query", field = "hello")
        fun someFetcher(dfe: DgsDataFetchingEnvironment): CompletableFuture<String> {
            val loader: DataLoader<String, String> = dfe.getDataLoader(ExampleBatchLoader::class.java)
            loader.load("a")
            loader.load("b")
            return loader.load("c")
        }
    }

    val helloFetcherWithField = object : Any() {
        @DgsData(parentType = "Query", field = "hello")
        fun someFetcher(dfe: DgsDataFetchingEnvironment): CompletableFuture<String> {
            val loader: DataLoader<String, String> = dfe.getDataLoader(ExampleBatchLoaderFromField::class.java)
            loader.load("a")
            loader.load("b")
            return loader.load("c")
        }
    }

    val helloFetcherWithMultipleField = object : Any() {
        @DgsData(parentType = "Query", field = "hello")
        fun someFetcher(dfe: DgsDataFetchingEnvironment): CompletableFuture<String> {
            val loader: DataLoader<String, String> = dfe.getDataLoader(ExampleMultipleBatchLoadersAsField::class.java)
            loader.load("a")
            loader.load("b")
            return loader.load("c")
        }
    }

    val helloFetcherMapped = object : Any() {
        @DgsData(parentType = "Query", field = "hello")
        fun someFetcher(dfe: DgsDataFetchingEnvironment): CompletableFuture<String> {
            val loader = dfe.getDataLoader<String, String>("exampleMappedLoader")
            loader.load("a")
            loader.load("b")
            return loader.load("c")
        }
    }

    val helloFetcherWithFieldMapped = object : Any() {
        @DgsData(parentType = "Query", field = "hello")
        fun someFetcher(dfe: DgsDataFetchingEnvironment): CompletableFuture<String> {
            val loader: DataLoader<String, String> = dfe.getDataLoader(ExampleMappedBatchLoaderFromField::class.java)
            loader.load("a")
            loader.load("b")
            return loader.load("c")
        }
    }

    val helloFetcherWithBasicDFE = object : Any() {
        @DgsData(parentType = "Query", field = "hello")
        fun someFetcher(dfe: DataFetchingEnvironment): CompletableFuture<String> {
            // val loader: DataLoader<String, String> = dfe.getDataLoader<String, String>(ExampleBatchLoader::class.java)
            val loader = dfe.getDataLoader<String, String>("exampleLoader")
            loader.load("a")
            loader.load("b")
            return loader.load("c")
        }
    }

    @BeforeEach
    fun setDataLoaderInstrumentationExtensionProvider() {
        val listableBeanFactory = StaticListableBeanFactory()
        every { applicationContextMock.getBeanProvider(DataLoaderInstrumentationExtensionProvider::class.java) } returns
            listableBeanFactory.getBeanProvider(DataLoaderInstrumentationExtensionProvider::class.java)

        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
    }

    @Test
    fun getDataLoader() {
        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(Pair("helloFetcher", helloFetcher))
        every { applicationContextMock.getBeansWithAnnotation(DgsDataLoader::class.java) } returns mapOf(Pair("helloLoader", ExampleBatchLoader()))
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()
        val provider = DgsDataLoaderProvider(applicationContextMock)
        provider.findDataLoaders()
        val dataLoaderRegistry = provider.buildRegistry()

        val schemaProvider = DgsSchemaProvider(applicationContextMock, Optional.empty(), Optional.empty(), Optional.empty())
        val schema = schemaProvider.schema()
        val build = GraphQL.newGraphQL(schema).build()

        val executionInput: ExecutionInput = ExecutionInput.newExecutionInput()
            .query("{hello}")
            .dataLoaderRegistry(dataLoaderRegistry)
            .build()
        val executionResult = build.execute(executionInput)
        Assertions.assertTrue(executionResult.isDataPresent)
        val result = executionResult.getData() as Map<String, String>
        Assertions.assertEquals("c", result["hello"])
    }

    @Test
    fun getDataLoaderWithBasicDFE() {
        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(Pair("helloFetcher", helloFetcherWithBasicDFE))
        every { applicationContextMock.getBeansWithAnnotation(DgsDataLoader::class.java) } returns mapOf(Pair("helloLoader", ExampleBatchLoader()))
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()
        val provider = DgsDataLoaderProvider(applicationContextMock)
        provider.findDataLoaders()
        val dataLoaderRegistry = provider.buildRegistry()

        val schemaProvider = DgsSchemaProvider(applicationContextMock, Optional.empty(), Optional.empty(), Optional.empty())
        val schema = schemaProvider.schema()
        val build = GraphQL.newGraphQL(schema).build()

        val executionInput: ExecutionInput = ExecutionInput.newExecutionInput()
            .query("{hello}")
            .dataLoaderRegistry(dataLoaderRegistry)
            .build()
        val executionResult = build.execute(executionInput)
        Assertions.assertTrue(executionResult.isDataPresent)
        val result = executionResult.getData() as Map<String, String>
        Assertions.assertEquals("c", result["hello"])
    }

    @Test
    fun getDataLoaderFromField() {
        every { applicationContextMock.getBeansWithAnnotation(DgsDataLoader::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(Pair("helloFetcher", helloFetcherWithField), Pair("helloLoader", ExampleBatchLoaderFromField()))

        val provider = DgsDataLoaderProvider(applicationContextMock)
        provider.findDataLoaders()
        val dataLoaderRegistry = provider.buildRegistry()

        val schemaProvider = DgsSchemaProvider(applicationContextMock, Optional.empty(), Optional.empty(), Optional.empty())
        val schema = schemaProvider.schema()
        val build = GraphQL.newGraphQL(schema).build()

        val executionInput: ExecutionInput = ExecutionInput.newExecutionInput()
            .query("{hello}")
            .dataLoaderRegistry(dataLoaderRegistry)
            .build()
        val executionResult = build.execute(executionInput)
        Assertions.assertTrue(executionResult.isDataPresent)
        val result = executionResult.getData() as Map<String, String>
        Assertions.assertEquals("c", result["hello"])
    }

    @Test
    fun getMultipleDataLoadersFromField() {
        every { applicationContextMock.getBeansWithAnnotation(DgsDataLoader::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(Pair("helloFetcher", helloFetcherWithMultipleField), Pair("helloLoader", ExampleMultipleBatchLoadersAsField()))

        val provider = DgsDataLoaderProvider(applicationContextMock)
        provider.findDataLoaders()
        val dataLoaderRegistry = provider.buildRegistry()

        val schemaProvider = DgsSchemaProvider(applicationContextMock, Optional.empty(), Optional.empty(), Optional.empty())
        val schema = schemaProvider.schema()
        val build = GraphQL.newGraphQL(schema).build()

        val executionInput: ExecutionInput = ExecutionInput.newExecutionInput()
            .query("{hello}")
            .dataLoaderRegistry(dataLoaderRegistry)
            .build()
        val executionResult = build.execute(executionInput)
        assert(executionResult.errors.size > 0)
    }

    @Test
    fun getMappedDataLoader() {
        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(Pair("helloFetcher", helloFetcherMapped))
        every { applicationContextMock.getBeansWithAnnotation(DgsDataLoader::class.java) } returns mapOf(Pair("helloLoader", ExampleMappedBatchLoader()))
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()
        val provider = DgsDataLoaderProvider(applicationContextMock)
        provider.findDataLoaders()
        val dataLoaderRegistry = provider.buildRegistry()

        val schemaProvider = DgsSchemaProvider(applicationContextMock, Optional.empty(), Optional.empty(), Optional.empty())
        val schema = schemaProvider.schema()
        val build = GraphQL.newGraphQL(schema).build()

        val executionInput: ExecutionInput = ExecutionInput.newExecutionInput()
            .query("{hello}")
            .dataLoaderRegistry(dataLoaderRegistry)
            .build()
        val executionResult = build.execute(executionInput)
        Assertions.assertTrue(executionResult.isDataPresent)
        val result = executionResult.getData() as Map<String, String>
        Assertions.assertEquals("C", result["hello"])
    }

    @Test
    fun getMappedDataLoaderFromField() {
        every { applicationContextMock.getBeansWithAnnotation(DgsDataLoader::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(Pair("helloFetcher", helloFetcherWithFieldMapped), Pair("helloLoader", ExampleMappedBatchLoaderFromField()))

        val provider = DgsDataLoaderProvider(applicationContextMock)
        provider.findDataLoaders()
        val dataLoaderRegistry = provider.buildRegistry()

        val schemaProvider = DgsSchemaProvider(applicationContextMock, Optional.empty(), Optional.empty(), Optional.empty())
        val schema = schemaProvider.schema()
        val build = GraphQL.newGraphQL(schema).build()

        val executionInput: ExecutionInput = ExecutionInput.newExecutionInput()
            .query("{hello}")
            .dataLoaderRegistry(dataLoaderRegistry)
            .build()
        val executionResult = build.execute(executionInput)
        Assertions.assertTrue(executionResult.isDataPresent)
        val result = executionResult.getData() as Map<String, String>
        Assertions.assertEquals("C", result["hello"])
    }

    @Suppress("DEPRECATION")
    @Test
    fun verifyDelegation() {
        val dgsDfe = DgsDataFetchingEnvironment(dfeMock)

        dgsDfe.getSource<String>()
        verify { dfeMock.getSource() }

        dgsDfe.getArgument<String>("name")
        verify { dfeMock.getArgument("name") }

        dgsDfe.arguments
        verify { dfeMock.arguments }

        dgsDfe.containsArgument("name")
        verify { dfeMock.containsArgument("name") }

        dgsDfe.getArgument<String>("name")
        verify { dfeMock.getArgument<String>("name") }

        dgsDfe.getArgumentOrDefault("name", "")
        verify { dfeMock.getArgumentOrDefault("name", "") }

        dgsDfe.getContext<String>()
        verify { dfeMock.getContext<String>() }

        dgsDfe.getLocalContext<String>()
        verify { dfeMock.getLocalContext() }

        dgsDfe.getRoot<String>()
        verify { dfeMock.getRoot() }

        dgsDfe.fieldDefinition
        verify { dfeMock.fieldDefinition }

        @Suppress("DEPRECATION")
        dgsDfe.fields
        verify { dfeMock.fields }

        dgsDfe.mergedField
        verify { dfeMock.mergedField }

        dgsDfe.field
        verify { dfeMock.field }

        dgsDfe.fieldType
        verify { dfeMock.fieldType }

        dgsDfe.executionStepInfo
        verify { dfeMock.executionStepInfo }

        dgsDfe.parentType
        verify { dfeMock.parentType }

        dgsDfe.graphQLSchema
        verify { dfeMock.graphQLSchema }

        dgsDfe.fragmentsByName
        verify { dfeMock.fragmentsByName }

        dgsDfe.executionId
        verify { dfeMock.executionId }

        dgsDfe.selectionSet
        verify { dfeMock.selectionSet }

        dgsDfe.queryDirectives
        verify { dfeMock.queryDirectives }

        dgsDfe.getDataLoader<String, String>("loaderA")
        verify { dfeMock.getDataLoader<String, String>("loaderA") }

        dgsDfe.dataLoaderRegistry
        verify { dfeMock.dataLoaderRegistry }

        dgsDfe.cacheControl
        verify { dfeMock.cacheControl }

        dgsDfe.locale
        verify { dfeMock.locale }

        dgsDfe.operationDefinition
        verify { dfeMock.operationDefinition }

        dgsDfe.document
        verify { dfeMock.document }

        dgsDfe.variables
        verify { dfeMock.variables }
    }
}
