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

interface DgsDataLoaderInstrumentationContext {
    /**
     * onComplete will run in a whenComplete attached to the data loader's returned CompletableFuture.
     *
     * @param result the actual results of the data loader. Will be a Map or List depending on the type of data loader.
     * @param exception any exception thrown by the data loader
     *
     * This means it will run on one of two possible threads:
     *  - the thread associated with the data loader's CompletableFuture
     *  - the thread that actually dispatched the data loader
     */
    fun onComplete(result: Any?, exception: Any?)
}
