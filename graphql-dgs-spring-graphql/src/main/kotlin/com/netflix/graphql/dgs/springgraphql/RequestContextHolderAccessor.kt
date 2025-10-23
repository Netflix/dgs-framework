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

package com.netflix.graphql.dgs.springgraphql

import io.micrometer.context.ThreadLocalAccessor
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.RequestContextHolder

internal class RequestContextHolderAccessor : ThreadLocalAccessor<RequestAttributes> {
    companion object {
        private const val ACCESSOR_KEY = "dgs.spring.request-context"
    }

    override fun key() = ACCESSOR_KEY

    override fun getValue(): RequestAttributes? = RequestContextHolder.getRequestAttributes()

    override fun setValue(value: RequestAttributes) {
        RequestContextHolder.setRequestAttributes(value)
    }

    override fun setValue() {
        RequestContextHolder.resetRequestAttributes()
    }
}
