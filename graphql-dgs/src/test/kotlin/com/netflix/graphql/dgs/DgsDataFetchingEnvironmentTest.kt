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

package com.netflix.graphql.dgs

import com.netflix.graphql.dgs.internal.DefaultDgsDataLoaderProvider
import com.netflix.graphql.dgs.internal.DgsSchemaProvider
import com.netflix.graphql.dgs.internal.method.DataFetchingEnvironmentArgumentResolver
import com.netflix.graphql.dgs.internal.method.MethodDataFetcherFactory
import graphql.ExecutionInput
import graphql.GraphQL
import graphql.schema.DataFetchingEnvironment
import org.dataloader.DataLoader
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.ApplicationContext
import java.util.Optional
import java.util.concurrent.CompletableFuture
import kotlin.reflect.KClass

internal class DgsDataFetchingEnvironmentTest {
    private val contextRunner = ApplicationContextRunner()

    @DgsComponent
    class HelloFetcher {
        @DgsData(parentType = "Query", field = "hello")
        fun someFetcher(dfe: DgsDataFetchingEnvironment): CompletableFuture<String> {
            val loader: DataLoader<String, String> = dfe.getDataLoader(ExampleBatchLoader::class.java)
            loader.load("a")
            loader.load("b")
            return loader.load("c")
        }
    }

    @DgsComponent
    class HelloFetcherWithField {
        @DgsData(parentType = "Query", field = "hello")
        fun someFetcher(dfe: DgsDataFetchingEnvironment): CompletableFuture<String> {
            val loader: DataLoader<String, String> = dfe.getDataLoader(ExampleBatchLoaderFromField::class.java)
            loader.load("a")
            loader.load("b")
            return loader.load("c")
        }
    }

    @DgsComponent
    class HelloFetcherWithMultipleField {
        @DgsData(parentType = "Query", field = "hello")
        fun someFetcher(dfe: DgsDataFetchingEnvironment): CompletableFuture<String> {
            val loader: DataLoader<String, String> = dfe.getDataLoader(ExampleMultipleBatchLoadersAsField::class.java)
            loader.load("a")
            loader.load("b")
            return loader.load("c")
        }
    }

    @DgsComponent
    class HelloFetcherMapped {
        @DgsData(parentType = "Query", field = "hello")
        fun someFetcher(dfe: DgsDataFetchingEnvironment): CompletableFuture<String> {
            val loader =
                dfe.getDataLoader<String, String>("exampleMappedLoader")
                    ?: throw AssertionError("exampleMappedLoader not found")
            loader.load("a")
            loader.load("b")
            return loader.load("c")
        }
    }

    @DgsComponent
    class HelloFetcherWithFieldMapped {
        @DgsData(parentType = "Query", field = "hello")
        fun someFetcher(dfe: DgsDataFetchingEnvironment): CompletableFuture<String> {
            val loader: DataLoader<String, String> = dfe.getDataLoader(ExampleMappedBatchLoaderFromField::class.java)
            loader.load("a")
            loader.load("b")
            return loader.load("c")
        }
    }

    @DgsComponent
    class HelloFetcherWithBasicDfe {
        @DgsData(parentType = "Query", field = "hello")
        fun someFetcher(dfe: DataFetchingEnvironment): CompletableFuture<String> {
            val loader =
                dfe.getDataLoader<String, String>("exampleLoader")
                    ?: throw AssertionError("exampleLoader not found")
            loader.load("a")
            loader.load("b")
            return loader.load("c")
        }
    }

    @Test
    fun getDataLoader() {
        contextRunner.withBeans(ExampleBatchLoader::class, HelloFetcher::class).run { context ->
            validateDataLoader(context)
        }
    }

    @Test
    fun getDataLoaderWithBasicDfe() {
        contextRunner.withBeans(HelloFetcherWithBasicDfe::class, ExampleBatchLoader::class).run { context ->
            validateDataLoader(context)
        }
    }

    private fun validateDataLoader(context: ApplicationContext) {
        val provider = DefaultDgsDataLoaderProvider(context)
        provider.findDataLoaders()
        val dataLoaderRegistry = provider.buildRegistry()

        val schemaProvider =
            DgsSchemaProvider(
                applicationContext = context,
                federationResolver = Optional.empty(),
                existingTypeDefinitionRegistry = Optional.empty(),
                methodDataFetcherFactory = MethodDataFetcherFactory(listOf(DataFetchingEnvironmentArgumentResolver(context))),
            )
        val schema = schemaProvider.schema().graphQLSchema
        val build = GraphQL.newGraphQL(schema).build()

        val executionInput: ExecutionInput =
            ExecutionInput
                .newExecutionInput()
                .query("{hello}")
                .dataLoaderRegistry(dataLoaderRegistry)
                .build()
        val executionResult = build.execute(executionInput)
        Assertions.assertTrue(executionResult.isDataPresent)
        val result = executionResult.getData() as Map<String, String>
        Assertions.assertEquals("c", result["hello"])
    }

    @Test
    fun getDataLoaderFromBean() {
        contextRunner
            .withConfiguration(AutoConfigurations.of(ExampleBatchLoaderFromBean::class.java))
            .run { context ->
                validateDataLoader(context)
            }

        contextRunner
            .withConfiguration(AutoConfigurations.of(ExampleBatchLoaderFromBeanName::class.java))
            .run { context ->
                validateDataLoader(context)
            }
    }

    @Test
    fun getDataLoaderFromField() {
        contextRunner.withBeans(HelloFetcherWithField::class, ExampleBatchLoaderFromField::class).run { context ->
            validateDataLoader(context)
        }
    }

    @Test
    fun getMultipleDataLoadersFromField() {
        contextRunner.withBeans(HelloFetcherWithMultipleField::class, ExampleMultipleBatchLoadersAsField::class).run { context ->
            val provider = DefaultDgsDataLoaderProvider(context)
            provider.findDataLoaders()
            val dataLoaderRegistry = provider.buildRegistry()

            val schemaProvider =
                DgsSchemaProvider(
                    applicationContext = context,
                    federationResolver = Optional.empty(),
                    existingTypeDefinitionRegistry = Optional.empty(),
                    methodDataFetcherFactory = MethodDataFetcherFactory(listOf()),
                )

            val schema = schemaProvider.schema().graphQLSchema
            val build = GraphQL.newGraphQL(schema).build()

            val executionInput: ExecutionInput =
                ExecutionInput
                    .newExecutionInput()
                    .query("{hello}")
                    .dataLoaderRegistry(dataLoaderRegistry)
                    .build()
            val executionResult = build.execute(executionInput)
            assert(executionResult.errors.size > 0)
        }
    }

    @Test
    fun getMappedDataLoader() {
        contextRunner.withBeans(HelloFetcherMapped::class, ExampleMappedBatchLoader::class).run { context ->
            val provider = DefaultDgsDataLoaderProvider(context)
            provider.findDataLoaders()
            val dataLoaderRegistry = provider.buildRegistry()

            val schemaProvider =
                DgsSchemaProvider(
                    applicationContext = context,
                    federationResolver = Optional.empty(),
                    existingTypeDefinitionRegistry = Optional.empty(),
                    methodDataFetcherFactory = MethodDataFetcherFactory(listOf(DataFetchingEnvironmentArgumentResolver(context))),
                )

            val schema = schemaProvider.schema().graphQLSchema
            val build = GraphQL.newGraphQL(schema).build()

            val executionInput: ExecutionInput =
                ExecutionInput
                    .newExecutionInput()
                    .query("{hello}")
                    .dataLoaderRegistry(dataLoaderRegistry)
                    .build()
            val executionResult = build.execute(executionInput)
            Assertions.assertTrue(executionResult.isDataPresent)
            val result = executionResult.getData() as Map<String, String>
            Assertions.assertEquals("C", result["hello"])
        }
    }

    @Test
    fun getMappedDataLoaderFromField() {
        contextRunner.withBeans(HelloFetcherWithFieldMapped::class, ExampleMappedBatchLoaderFromField::class).run { context ->
            val provider = DefaultDgsDataLoaderProvider(context)
            provider.findDataLoaders()
            val dataLoaderRegistry = provider.buildRegistry()

            val schemaProvider =
                DgsSchemaProvider(
                    applicationContext = context,
                    federationResolver = Optional.empty(),
                    existingTypeDefinitionRegistry = Optional.empty(),
                    methodDataFetcherFactory = MethodDataFetcherFactory(listOf(DataFetchingEnvironmentArgumentResolver(context))),
                )

            val schema = schemaProvider.schema().graphQLSchema
            val build = GraphQL.newGraphQL(schema).build()

            val executionInput: ExecutionInput =
                ExecutionInput
                    .newExecutionInput()
                    .query("{hello}")
                    .dataLoaderRegistry(dataLoaderRegistry)
                    .build()
            val executionResult = build.execute(executionInput)
            Assertions.assertTrue(executionResult.isDataPresent)
            val result = executionResult.getData() as Map<String, String>
            Assertions.assertEquals("C", result["hello"])
        }
    }

    private fun ApplicationContextRunner.withBeans(vararg beanClasses: KClass<*>): ApplicationContextRunner {
        var context = this
        for (klazz in beanClasses) {
            context = context.withBean(klazz.java)
        }
        return context
    }
}
