package com.netflix.graphql.dgs.transports.websockets

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

    fun isConnectionInitNotProcessed(): Boolean {
        return !this.connectionInitReceived
    }

    fun dispose() {
        subscriptions.forEach { (t, subscription) ->

            try {
                subscription.cancel()
            } catch (e: Throwable) {
                // Ignore and keep on
            }
        }
        this.subscriptions.clear()
    }
}
