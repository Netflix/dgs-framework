/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.graphql.dgs.internal.utils

import org.slf4j.Logger
import org.slf4j.event.Level
import java.time.Duration

object TimeTracer {
    fun <R> logTime(action: () -> R, logger: Logger, message: String): R {
        val startTime = System.currentTimeMillis()
        val result = action()
        val endTime = System.currentTimeMillis()
        val totalTime = endTime - startTime

        logger.debug(message, totalTime)

        return result
    }

    @JvmStatic
    fun <R> measureAndLogTotalMillisecondsElapsedFor(
        logger: org.slf4j.Logger,
        messageTemplate: String,
        action: () -> R
    ): R {
        return Measurement.measureAndLogTotalMillisecondsElapsed(
            logger = logger,
            level = Level.DEBUG,
            messageForTotalTimeMs = { ms: Long -> messageTemplate.format(ms) }
        ).run(action).getActionResultOrElseThrow()
    }

    @JvmStatic
    fun <R> measureAndLogTotalMillisecondsElapsedFor(
        logger: reactor.util.Logger,
        messageTemplate: String,
        action: () -> R
    ): R {
        return Measurement.measureAndLogTotalMillisecondsElapsed(
            logger = logger,
            level = Level.DEBUG,
            messageForTotalTimeMs = { ms: Long -> messageTemplate.format(ms) }
        ).run(action).getActionResultOrElseThrow()
    }

    @JvmStatic
    fun <R> measureAndLogTotalDurationFor(
        logger: org.slf4j.Logger,
        messageCreator: (Duration) -> String,
        action: () -> R
    ): R {
        return Measurement.measureAndLogDurationOf(
            logger = logger, level = Level.DEBUG, messageForTotalDuration = messageCreator
        ).run(action).getActionResultOrElseThrow()
    }

    @JvmStatic
    fun <R> measureAndLogTotalDurationFor(
        logger: reactor.util.Logger,
        messageCreator: (Duration) -> String,
        action: () -> R
    ): R {
        return Measurement.measureAndLogDurationOf(
            logger = logger, level = Level.DEBUG, messageForTotalDuration = messageCreator
        ).run(action).getActionResultOrElseThrow()
    }

    fun <R> org.slf4j.Logger.logTotalMillisecondsElapsedWith(messageTemplate: String, action: () -> R): R {
        return measureAndLogTotalMillisecondsElapsedFor(this, messageTemplate, action)
    }

    fun <R> reactor.util.Logger.logTotalMillisecondsElapsedWith(messageTemplate: String, action: () -> R): R {
        return measureAndLogTotalMillisecondsElapsedFor(this, messageTemplate, action)
    }

    fun <R> org.slf4j.Logger.logTotalDurationWith(messageCreator: (Duration) -> String, action: () -> R): R {
        return measureAndLogTotalDurationFor(this, messageCreator, action)
    }

    fun <R> reactor.util.Logger.logTotalDurationWith(messageCreator: (Duration) -> String, action: () -> R): R {
        return measureAndLogTotalDurationFor(this, messageCreator, action)
    }
}
