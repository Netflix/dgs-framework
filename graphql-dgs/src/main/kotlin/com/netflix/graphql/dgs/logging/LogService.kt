package com.netflix.graphql.dgs.logging

/**
 * Generic interface to log to external log services.
 */
interface LogService {
    fun publishLog(logEvent: LogEvent)
}