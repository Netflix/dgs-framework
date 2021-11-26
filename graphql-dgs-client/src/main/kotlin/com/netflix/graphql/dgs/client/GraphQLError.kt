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

package com.netflix.graphql.dgs.client

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * A representation of a GraphQL error, following the format used by a DGS and Gateway.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class GraphQLError(
    @JsonProperty val message: String = "",
    @JsonProperty val path: List<Any> = emptyList(),
    @JsonProperty val locations: List<Any> = emptyList(),
    @JsonProperty val extensions: GraphQLErrorExtensions?
) {
    val pathAsString = path.joinToString(".")
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class GraphQLErrorExtensions(
    @JsonProperty val errorType: ErrorType? = null,
    @JsonProperty val errorDetail: String? = null,
    @JsonProperty val origin: String = "",
    @JsonProperty val debugInfo: GraphQLErrorDebugInfo = GraphQLErrorDebugInfo(),
    @JsonProperty val classification: String = ""
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GraphQLErrorDebugInfo(
    @JsonProperty val subquery: String = "",
    @JsonProperty val variables: Map<String, Any> = emptyMap()
)

/**
The value of the errorType field is an enumeration of error types.
An errorType is a fairly coarse characterization of an error that should be sufficient for client side branching logic.
The enumeration of error types should remain mostly static.
More specific errors may be specified by the errorDetail field.

Http mappings are provided only as documentation.
These are rough mappings intended to provide a quick explanation of the semantics by analogy with HTTP.

@See https://docs.google.com/document/d/1FX5K0C1pyySayFmRt53FptUQ8vCf__y_WduaKT8HsbM
 */
enum class ErrorType {
    @JsonEnumDefaultValue
    /**
     Unknown error.

     For example, this error may be returned when
     an error code received from another address space belongs to
     an error space that is not known in this address space.  Also
     errors raised by APIs that do not return enough error information
     may be converted to this error.

     If a client sees an unknown errorType, it will be interpreted as UNKNOWN.
     Unknown errors MUST NOT trigger any special behavior. These MAY be treated
     by an implementation as being equivalent to INTERNAL.

     When possible, a more specific error should be provided.

     HTTP Mapping: 520 Unknown Error
     */
    UNKNOWN,

    /**
     Internal error.

     An unexpected internal error was encountered. This means that some
     invariants expected by the underlying system have been broken.
     This error code is reserved for serious errors.

     HTTP Mapping: 500 Internal Server Error
     */
    INTERNAL,

    /**
     The requested entity was not found.

     This could apply to a resource that has never existed (e.g. bad resource id),
     or a resource that no longer exists (e.g. cache expired.)

     Note to server developers: if a request is denied for an entire class
     of users, such as gradual feature rollout or undocumented allowlist,
     `NOT_FOUND` may be used. If a request is denied for some users within
     a class of users, such as user-based access control, `PERMISSION_DENIED`
     must be used.

     HTTP Mapping: 404 Not Found
     */
    NOT_FOUND,

    /**
     The request does not have valid authentication credentials.

     This is intended to be returned only for routes that require
     authentication.

     HTTP Mapping: 401 Unauthorized
     */
    UNAUTHENTICATED,

    /**
     The caller does not have permission to execute the specified
     operation.

     `PERMISSION_DENIED` must not be used for rejections
     caused by exhausting some resource or quota.
     `PERMISSION_DENIED` must not be used if the caller
     cannot be identified (use `UNAUTHENTICATED`
     instead for those errors).

     This error Type does not imply the
     request is valid or the requested entity exists or satisfies
     other pre-conditions.

     HTTP Mapping: 403 Forbidden
     */
    PERMISSION_DENIED,

    /**
     Bad Request.

     There is a problem with the request.
     Retrying the same request is not likely to succeed.
     An example would be a query or argument that cannot be deserialized.

     HTTP Mapping: 400 Bad Request
     */
    BAD_REQUEST,

    /**
     Currently Unavailable.

     The service is currently unavailable.  This is most likely a
     transient condition, which can be corrected by retrying with
     a backoff.

     HTTP Mapping: 503 Unavailable
     */
    UNAVAILABLE,

    /**
     The operation was rejected because the system is not in a state
     required for the operation's execution.  For example, the directory
     to be deleted is non-empty, an rmdir operation is applied to
     a non-directory, etc.

     Service implementers can use the following guidelines to decide
     between `FAILED_PRECONDITION` and `UNAVAILABLE`:

     - Use `UNAVAILABLE` if the client can retry just the failing call.
     - Use `FAILED_PRECONDITION` if the client should not retry until
     the system state has been explicitly fixed.  E.g., if an "rmdir"
     fails because the directory is non-empty, `FAILED_PRECONDITION`
     should be returned since the client should not retry unless
     the files are deleted from the directory.

     HTTP Mapping: 400 Bad Request or 500 Internal Server Error
     */
    FAILED_PRECONDITION
}
