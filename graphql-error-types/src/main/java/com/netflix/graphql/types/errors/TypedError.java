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

package com.netflix.graphql.types.errors;

import java.util.Map;

public interface TypedError {

    /**
     * The value of the errorType field is an enumeration of error types. An errorType is a fairly coarse
     * characterization of an error that should be sufficient for client side branching logic.
     *
     * @return ErrorType (Should NOT be Null)
     */
    ErrorType getErrorType();

    /**
     * The ErrorDetail is an optional field which will provide more fine grained information on the error condition.
     * This allows the ErrorType enumeration to be small and mostly static so that application branching logic
     * can depend on it. The ErrorDetail provides a more specific cause for the error. This enumeration will
     * be much larger and likely change/grow over time.
     *
     * @return ErrorDetail (May be null)
     */
    ErrorDetail getErrorDetail();

    /**
     * Indicates the source that issued the error. For example, could
     * be a backend service name, a domain graph service name, or a
     * gateway. In the case of client code throwing the error, this
     * may be a client library name, or the client app name.
     *
     * @return origin of the error (May be null)
     */
    String getOrigin();

    /**
     * Http URI to a page detailing additional
     * information that could be used to debug
     * the error. This information may be general
     * to the class of error or specific to this
     * particular instance of the error.
     *
     * @return Debug URI (May be null)
     */
    String getDebugUri();

    /**
     * Optionally provided based on request flag
     * Could include e.g. stacktrace or info from
     * upstream service
     *
     * @return map of debugInfo (May be null)
     */
    Map<String, Object> getDebugInfo();

}
