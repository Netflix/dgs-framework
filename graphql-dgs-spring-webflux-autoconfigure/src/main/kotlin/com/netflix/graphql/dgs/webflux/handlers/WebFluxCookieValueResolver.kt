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

package com.netflix.graphql.dgs.webflux.handlers

import com.netflix.graphql.dgs.internal.CookieValueResolver
import com.netflix.graphql.dgs.internal.DgsRequestData
import com.netflix.graphql.dgs.reactive.internal.DgsReactiveRequestData
import org.springframework.http.HttpCookie
import org.springframework.util.MultiValueMap

class WebFluxCookieValueResolver : CookieValueResolver {
    override fun getCookieValue(name: String, requestData: DgsRequestData?): String? {

        return if (requestData is DgsReactiveRequestData) {
            val cookies: MultiValueMap<String, HttpCookie>? = requestData.serverRequest?.cookies()
            cookies?.getFirst(name)?.value
        } else null
    }
}
