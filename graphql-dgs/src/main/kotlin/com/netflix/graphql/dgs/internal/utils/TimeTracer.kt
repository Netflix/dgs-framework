package com.netflix.graphql.dgs.internal.utils

import org.slf4j.Logger

object TimeTracer {
    fun <R> logTime(action: () -> R, logger: Logger, message: String): R {
        val startTime = System.currentTimeMillis()
        val result = action()
        val endTime = System.currentTimeMillis()
        val totalTime = endTime - startTime

        logger.debug(message, totalTime)

        return result
    }
}