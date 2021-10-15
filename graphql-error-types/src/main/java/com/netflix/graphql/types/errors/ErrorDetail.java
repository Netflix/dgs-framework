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

package com.netflix.graphql.types.errors;

import graphql.ErrorClassification;
import graphql.GraphQLError;

import static com.netflix.graphql.types.errors.ErrorType.*;

/**
 * The ErrorDetail is an optional field which will provide more fine grained information on the error condition.
 * This allows the ErrorType enumeration to be small and mostly static so that application branching logic
 * can depend on it. The ErrorDetail provides a more specific cause for the error. This enumeration will
 * be much larger and likely change/grow over time.
 * <p>
 * For example, a service may be unavailable, resulting in ErrorType UNAVAILABLE. The ErrorDetail may be
 * more specific, such as THROTTLED_CPU. The ErrorType should be sufficient to control client behavior,
 * while the ErrorDetail is more useful for debugging and real-time operations.
 * <p>
 * The example below is not to be considered canonical. This enumeration may be defined by the server,
 * and should be included with the server schema.
 */
public interface ErrorDetail extends ErrorClassification {
    ErrorType getErrorType();

    /**
     * A set of common error details. Implementations may define additional values.
     */
    enum Common implements ErrorDetail {

        /**
         * The deadline expired before the operation could complete.
         * <p>
         * For operations that change the state of the system, this error
         * may be returned even if the operation has completed successfully.
         * For example, a successful response from a server could have been
         * delayed long enough for the deadline to expire.
         * <p>
         * HTTP Mapping: 504 Gateway Timeout
         * Error Type: UNAVAILABLE
         */
        DEADLINE_EXCEEDED(UNAVAILABLE),

        /**
         * The server detected that the client is exhibiting a behavior that
         * might be generating excessive load.
         * <p>
         * HTTP Mapping: 429 Too Many Requests or 420 Enhance Your Calm
         * Error Type: UNAVAILABLE
         */
        ENHANCE_YOUR_CALM(UNAVAILABLE),

        /**
         * The requested field is not found in the schema.
         * <p>
         * This differs from `NOT_FOUND` in that `NOT_FOUND` should be used when a
         * query is valid, but is unable to return a result (if, for example, a
         * specific video id doesn't exist). `FIELD_NOT_FOUND` is intended to be
         * returned by the server to signify that the requested field is not known to exist.
         * This may be returned in lieu of failing the entire query.
         * See also `PERMISSION_DENIED` for cases where the
         * requested field is invalid only for the given user or class of users.
         * <p>
         * HTTP Mapping: 404 Not Found
         * Error Type: BAD_REQUEST
         */
        FIELD_NOT_FOUND(BAD_REQUEST),

        /**
         * The client specified an invalid argument.
         * <p>
         * Note that this differs from `FAILED_PRECONDITION`.
         * `INVALID_ARGUMENT` indicates arguments that are problematic
         * regardless of the state of the system (e.g., a malformed file name).
         * <p>
         * HTTP Mapping: 400 Bad Request
         * Error Type: BAD_REQUEST
         * INVALID_ARGUMENT
         */
        INVALID_ARGUMENT(BAD_REQUEST),

        /**
         * The provided cursor is not valid.
         * <p>
         * The most common usage for this error is when a client is paginating
         * through a list that uses stateful cursors. In that case, the provided
         * cursor may be expired.
         * <p>
         * HTTP Mapping: 404 Not Found
         * Error Type: NOT_FOUND
         */
        INVALID_CURSOR(NOT_FOUND),

        /**
         * Unable to perform operation because a required resource is missing.
         * <p>
         * Example: Client is attempting to refresh a list, but the specified
         * list is expired. This requires an action by the client to get a new list.
         * <p>
         * If the user is simply trying GET a resource that is not found,
         * use the NOT_FOUND error type. FAILED_PRECONDITION.MISSING_RESOURCE
         * is to be used particularly when the user is performing an operation
         * that requires a particular resource to exist.
         * <p>
         * HTTP Mapping: 400 Bad Request or 500 Internal Server Error
         * Error Type: FAILED_PRECONDITION
         */
        MISSING_RESOURCE(FAILED_PRECONDITION),

        /**
         * Indicates the operation conflicts with the current state of the target resource.
         * Conflicts are most likely to occur in the context of a mutation.
         * <p>
         * For example, you may get a CONFLICT when writing a field with a reference value that is
         * older than the one already on the server, resulting in a version control conflict.
         * <p>
         * HTTP Mapping: 409 Conflict.
         * Error Type: FAILED_PRECONDITION
         */
        CONFLICT(FAILED_PRECONDITION),

        /**
         * Service Error.
         * <p>
         * There is a problem with an upstream service.
         * <p>
         * This may be returned if a gateway receives an unknown error from a service
         * or if a service is unreachable.
         * If a request times out which waiting on a response from a service,
         * `DEADLINE_EXCEEDED` may be returned instead.
         * If a service returns a more specific error Type, the specific error Type may
         * be returned instead.
         * <p>
         * HTTP Mapping: 502 Bad Gateway
         * Error Type: UNAVAILABLE
         */
        SERVICE_ERROR(UNAVAILABLE),

        /**
         * Request throttled based on server CPU limits
         * <p>
         * HTTP Mapping: 503 Unavailable.
         * Error Type: UNAVAILABLE
         */
        THROTTLED_CONCURRENCY(UNAVAILABLE),

        /**
         * Request throttled based on server concurrency limits.
         * <p>
         * HTTP Mapping: 503 Unavailable
         * Error Type: UNAVAILABLE
         */
        THROTTLED_CPU(UNAVAILABLE),

        /**
         * The operation is not implemented or is not currently supported/enabled.
         * <p>
         * HTTP Mapping: 501 Not Implemented
         * Error Type: BAD_REQUEST
         */
        UNIMPLEMENTED(BAD_REQUEST);

        private final ErrorType errorType;

        Common(ErrorType errorType) {
            this.errorType = errorType;
        }

        @Override
        public Object toSpecification(GraphQLError error) {
            return errorType + "." + this;
        }

        @Override
        public ErrorType getErrorType() {
            return errorType;
        }
    }
}
