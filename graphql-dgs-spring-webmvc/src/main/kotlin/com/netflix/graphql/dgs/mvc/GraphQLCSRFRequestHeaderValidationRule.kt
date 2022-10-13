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

package com.netflix.graphql.dgs.mvc

import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.util.StringUtils

/**
 * Implementation of a [GraphQLRequestHeaderValidationRule] that will make sure that the HTTP Request
 * Has either a content-type that enforces a _pre-flight_ check or has a preflight header, defined below.
 * A content-type that enforces a _pre-flight_ check shouldn't be any of the content-types defined in [NON_PREFLIGHTED_CONTENT_TYPES].
 * Which are the _pre-flight_ headers we support? See [GRAPHQL_PREFLIGHT_REQUESTS_HEADERS]
 *
 * What is a _pre-flight_ check?
 * It is a check that a common browser will do to enforce a [CORS policy](https://github.com/apollographql/apollo-server/security/advisories/GHSA-2p3c-p3qw-69r4).
 *
 * **Note**, is the responsibility of the applications to define a sensible CORS policy that will prevent a CSRF attack.
 */
class GraphQLCSRFRequestHeaderValidationRule : GraphQLRequestHeaderValidationRule {
    companion object {
        // CSRF Prevention Request Headers
        @Suppress("MemberVisibilityCanBePrivate")
        const val HEADER_X_APOLLO_OPERATION_NAME = "x-apollo-operation-name"

        @Suppress("MemberVisibilityCanBePrivate")
        const val HEADER_APOLLO_REQUIRE_PREFLIGHT = "apollo-require-preflight"

        @Suppress("MemberVisibilityCanBePrivate")
        const val HEADER_GRAPHQL_REQUIRE_PREFLIGHT = "graphql-require-preflight"

        /**
         * Headers, defined as `content-type`, that will not enforce a _preflight_ check by browsers.
         * In other words, if the `content-type` of the request matches any of these the browser will not enforce a CORS
         * check.
         *
         * @see https://developer.mozilla.org/en-US/docs/Web/HTTP/CORS
         */
        val NON_PREFLIGHTED_CONTENT_TYPES = setOf(
            MediaType.APPLICATION_FORM_URLENCODED,
            MediaType.MULTIPART_FORM_DATA,
            MediaType.TEXT_PLAIN
        )

        /**
         * Headers that should be available in case the request has either no `content-type` or one
         * that matches any of the [NON_PREFLIGHTED_CONTENT_TYPES].
         * Clients, which is the case with Apollo Client for example, *should always*  define a `content-type` even
         * if they are doing a `GET` request.
         *
         * Apollo Client Web, Apollo iOS, and Apollo Kotlin always send `x-apollo-operation-name` for example.
         *
         * @see https://github.com/apollographql/apollo-server/blob/version-4/packages/server/src/preventCsrf.ts
         */
        val GRAPHQL_PREFLIGHT_REQUESTS_HEADERS = listOf(
            HEADER_APOLLO_REQUIRE_PREFLIGHT,
            HEADER_X_APOLLO_OPERATION_NAME,
            HEADER_GRAPHQL_REQUIRE_PREFLIGHT
        ).map { it.lowercase() }.toSet()

        /**
         * > We don't want random websites to be able to execute actual GraphQL operations
         * > from a user's browser unless our CORS policy supports it. It's not good
         * > enough just to ensure that the browser can't read the response from the
         * > operation; we also want to prevent CSRF, where the attacker can cause side
         * > effects with an operation or can measure the timing of a read operation. Our
         * > goal is to ensure that we don't run the context function or execute the
         * > GraphQL operation until the browser has evaluated the CORS policy, which
         * > means we want all operations to be pre-flighted. We can do that by only
         * > processing operations that have at least one header set that appears to be
         * > manually set by the JS code rather than by the browser automatically.
         *
         * > POST requests generally have a content-type `application/json`, which is
         * > sufficient to trigger preflighting. So we take extra care with requests that
         * > specify no content-type or that specify one of the three non-preflighted
         * > content types. For those operations, we require (if this feature is enabled)
         * > one of a set of specific headers to be set. By ensuring that every operation
         * > either has a custom content-type or sets one of these headers, we know we
         * > won't execute operations at the request of origins who our CORS policy will
         * > block.
         *
         * From [Apollo Server](https://github.com/apollographql/apollo-server/blob/version-4/packages/server/src/preventCsrf.ts)
         */
        fun assertGraphQLCsrf(headers: HttpHeaders) {
            val contentType: MediaType? = headers.contentType
            if (contentType != null && isPreflightedContentType(contentType)) {
                // We managed to parse a MIME type that was not one of the
                // CORS-safe-listed ones. (Probably application/json!) That means that if
                // the client is a browser, the browser must have applied CORS
                // preflighting, and we don't have to worry about CSRF.
                return
            }
            // Either there was no content-type, or the content-type parsed properly as
            // one of the three CORS-safelisted values. Let's look for another header that
            // (if this was a browser) must have been set by the user's code and would
            // have caused a preflight.
            if (containsCSRFinFlightHeader(headers)) {
                return
            }
            throw DgsGraphQLRequestHeaderValidator.GraphQLRequestHeaderRuleException(
                "Expecting a CSRF Prevention Header but none was found, " +
                    "supported headers are $GRAPHQL_PREFLIGHT_REQUESTS_HEADERS."
            )
        }

        private fun isPreflightedContentType(mediaType: MediaType): Boolean {
            return NON_PREFLIGHTED_CONTENT_TYPES.find { it.isCompatibleWith(mediaType) } == null
        }

        private fun containsCSRFinFlightHeader(headers: HttpHeaders): Boolean {
            val csrfInFlightHeader: String? =
                headers.keys.find { GRAPHQL_PREFLIGHT_REQUESTS_HEADERS.contains(it.lowercase()) }
            return StringUtils.hasText(csrfInFlightHeader)
        }
    }

    override fun assert(headers: HttpHeaders) {
        assertGraphQLCsrf(headers)
    }
}
