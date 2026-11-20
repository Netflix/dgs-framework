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

import com.netflix.graphql.dgs.internal.DataFetcherResultProcessor
import graphql.TrivialDataFetcher
import graphql.schema.DataFetcher
import graphql.schema.DataFetcherFactories
import graphql.schema.DataFetchingEnvironment
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLCodeRegistry
import graphql.schema.GraphQLFieldDefinition
import org.springframework.context.ApplicationContext
import java.lang.reflect.Method

/**
 * Utility wrapper for [GraphQLCodeRegistry.Builder] which provides
 * a consistent registration mechanism of DataFetchers similar to the annotation-based approach.
 * Can be used as a first parameter of a [DgsCodeRegistry] annotated method.
 */
class DgsCodeRegistryBuilder
    @JvmOverloads
    constructor(
        private val dataFetcherResultProcessors: List<DataFetcherResultProcessor>,
        private val graphQLCodeRegistry: GraphQLCodeRegistry.Builder,
        private val ctx: ApplicationContext,
        private val methodDataFetcherRegistrar: MethodDataFetcherRegistrar? = null,
        private val instrumentationRecorder: InstrumentationRecorder? = null,
    ) {
        /**
         * Strategy used to register a method as a data fetcher with the same behavior as a [DgsData] annotated method:
         * argument resolution (e.g. `@InputArgument`), schema validation, and instrumentation settings.
         */
        fun interface MethodDataFetcherRegistrar {
            /**
             * @param context the [ApplicationContext] that [bean] belongs to, or `null` if [bean] belongs to the
             *   [ApplicationContext] of [builder].
             * @throws IllegalStateException if the schema build this registrar belongs to has finished.
             */
            fun register(
                builder: DgsCodeRegistryBuilder,
                bean: Any,
                method: Method,
                coordinates: FieldCoordinates,
                context: ApplicationContext?,
            )
        }

        /**
         * Strategy used to record the instrumentation settings of a data fetcher that has no [DgsData] method.
         */
        fun interface InstrumentationRecorder {
            /**
             * @throws IllegalStateException if the schema build this recorder belongs to has finished.
             */
            fun record(
                coordinates: FieldCoordinates,
                tracing: Boolean,
                metrics: Boolean,
            )
        }

        private var bindsEnvironmentToContext = false

        /**
         * Makes the data fetchers registered through this builder receive a [DgsDataFetchingEnvironment] backed by the
         * [ApplicationContext] of this builder, instead of the one DGS is configured with. Used for beans of another
         * [ApplicationContext], so that [DgsDataFetchingEnvironment.getDataLoader] resolves their data loaders.
         */
        internal fun bindingEnvironmentToContext(): DgsCodeRegistryBuilder = apply { bindsEnvironmentToContext = true }

        fun dataFetcher(
            coordinates: FieldCoordinates,
            dataFetcher: DataFetcher<*>,
        ): DgsCodeRegistryBuilder {
            val bound = if (bindsEnvironmentToContext) bindToContext(dataFetcher) else dataFetcher
            val fetcher =
                if (dataFetcherResultProcessors.isNotEmpty() && bound !is TrivialDataFetcher) {
                    DataFetcherFactories.wrapDataFetcher(bound) { dfe, result -> convertResult(dfe, result) }
                } else {
                    bound
                }

            graphQLCodeRegistry.dataFetcher(coordinates, fetcher)
            return this
        }

        private fun bindToContext(dataFetcher: DataFetcher<*>): DataFetcher<*> {
            fun bind(env: DataFetchingEnvironment) = env as? DgsDataFetchingEnvironment ?: DgsDataFetchingEnvironment(env, ctx)
            return if (dataFetcher is TrivialDataFetcher<*>) {
                TrivialDataFetcher { env -> dataFetcher.get(bind(env)) }
            } else {
                DataFetcher { env -> dataFetcher.get(bind(env)) }
            }
        }

        /**
         * Registers the given [method] on the given [bean] as the data fetcher for the given [coordinates],
         * as if it was annotated with [DgsData]. This means, among other things, that method arguments
         * are resolved the same way (`@InputArgument`, [DataFetchingEnvironment], etc.), the
         * method is validated against the schema when schema wiring validation is enabled, and tracing/metrics
         * instrumentation settings (including [DgsEnableDataFetcherInstrumentation]) are honored.
         *
         * The [bean] is not required to be a bean of the [ApplicationContext] DGS is configured with.
         * The [context] is the [ApplicationContext] that [bean] belongs to. When set, it backs the
         * [DgsDataFetchingEnvironment] that the method and the result processors receive for this data fetcher.
         * When `null`, the default, they receive the [ApplicationContext] DGS is configured with.
         *
         * @throws IllegalArgumentException if [method] is not declared by the class of [bean] or one of its supertypes.
         * @throws IllegalStateException if this builder was not provided by DGS, or if it is called after the
         *   [DgsCodeRegistry] method it was passed to has returned and the schema has been built.
         */
        @JvmOverloads
        fun dataFetcher(
            coordinates: FieldCoordinates,
            bean: Any,
            method: Method,
            context: ApplicationContext? = null,
        ): DgsCodeRegistryBuilder {
            require(method.declaringClass.isInstance(bean)) {
                "Method ${method.declaringClass.name}.${method.name} cannot be invoked on a bean of type ${bean.javaClass.name}"
            }
            val registrar =
                methodDataFetcherRegistrar
                    ?: throw IllegalStateException("This DgsCodeRegistryBuilder does not support registering method data fetchers")
            registrar.register(this, bean, method, coordinates, context)
            return this
        }

        /**
         * Sets the DGS instrumentation for the data fetcher at the given [coordinates].
         *
         * Should be used with a data fetcher registered with [dataFetcher].
         * DGS cannot read [DgsEnableDataFetcherInstrumentation] or the return type of data fetcher registered this way,
         * and therefore it's instrumented by default.
         *
         * - [tracing] (default `true`): DGS creates a span around each call. The span ends when `DataFetcher.get`
         *   returns and does not wait for a returned `CompletionStage`. Pass `false` for an asynchronous data
         *   fetcher. DGS uses the same setting for a `@DgsData` method that returns a `CompletionStage`.
         * - [metrics] (default `true`): DGS records the data fetcher in its metrics instrumentation.
         *
         * @throws IllegalStateException if this builder was not provided by DGS, or if it is called after the
         *   [DgsCodeRegistry] method it was passed to has returned and the schema has been built.
         */
        @JvmOverloads
        fun setInstrumentation(
            coordinates: FieldCoordinates,
            tracing: Boolean = true,
            metrics: Boolean = true,
        ): DgsCodeRegistryBuilder {
            val recorder =
                instrumentationRecorder
                    ?: throw IllegalStateException("This DgsCodeRegistryBuilder does not support setting instrumentation")
            recorder.record(coordinates, tracing, metrics)
            return this
        }

        fun hasDataFetcher(coordinates: FieldCoordinates): Boolean = graphQLCodeRegistry.hasDataFetcher(coordinates)

        fun getDataFetcher(
            coordinates: FieldCoordinates,
            fieldDefinition: GraphQLFieldDefinition,
        ): DataFetcher<*> = graphQLCodeRegistry.getDataFetcher(coordinates, fieldDefinition)

        private fun convertResult(
            dfe: DataFetchingEnvironment,
            result: Any?,
        ): Any? {
            if (result == null) {
                return null
            }
            val processor = dataFetcherResultProcessors.find { it.supportsType(result) } ?: return result
            val env =
                dfe as? DgsDataFetchingEnvironment ?: DgsDataFetchingEnvironment(dfe, ctx)
            return processor.process(result, env)
        }
    }
