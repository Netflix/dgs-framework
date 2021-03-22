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

import com.netflix.graphql.dgs.context.DgsContext
import com.netflix.graphql.dgs.internal.DgsRequestData

/**
 * A DgsContext is created for each request to hold state during that request.
 * This builder abstraction is used to plugin implementations with more or less functionality.
 * Typically not implemented by users. See [com.netflix.graphql.dgs.context.DgsCustomContextBuilder] instead for adding custom state to the context.
 */
@Deprecated("It is not recommended to create a custom DgsContextBuilder. Use a DgsCustomContextBuilder or DgsCustomContextBuilderWithRequest to add custom state to the context. This interface will be removed in the next major version.")
interface DgsContextBuilder {
    fun build(): DgsContext

    @JvmDefault
    fun build(dgsRequestData: DgsRequestData): DgsContext = build()
}
