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

package com.netflix.graphql.dgs.subscriptions.websockets

import org.reactivestreams.Subscription
import java.util.concurrent.ConcurrentHashMap

internal class Context<T>(
    /**
     * Indicates that the `ConnectionInit` message
     * has been received by the server. If this is
     * `true`, the client wont be kicked off after
     * the wait timeout has passed.
     */
    private var connectionInitReceived: Boolean = false

) {
    /**
     * Indicates that the connection was acknowledged
     * by having dispatched the `ConnectionAck` message
     * to the related client.
     */
    var acknowledged: Boolean = false

    /** The parameters passed during the connection initialisation. */
    var connectionParams: Map<String, Any>? = null

    /**
     * Holds the active subscriptions for this context. **All operations**
     * that are taking place are aggregated here. The user is _subscribed_
     * to an operation when waiting for result(s).
     */
    val subscriptions = ConcurrentHashMap<String, Subscription>()

    /**
     * An extra field where you can store your own context values
     * to pass between callbacks.
     */
    var extra: T? = null

    @Synchronized
    fun setConnectionInitReceived(): Boolean {
        val previousValue: Boolean = this.connectionInitReceived
        this.connectionInitReceived = true
        return previousValue
    }

    @Synchronized
    fun getConnectionInitReceived(): Boolean {
        return this.connectionInitReceived
    }
}
