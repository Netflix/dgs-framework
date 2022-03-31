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

package com.netflix.graphql.dgs.internal

import com.netflix.graphql.dgs.internal.utils.TimeTracer
import com.netflix.graphql.dgs.internal.utils.TimeTracer.logTotalMillisecondsElapsedWith
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import reactor.test.util.TestLogger
import java.time.Duration

class TimeTracerTest {

    @Test
    fun trackTimeOfSleepingThreadAction() {
        val logger: reactor.test.util.TestLogger = TestLogger()
        val messageTemplate: String = "my action took %s ms"
        val sum: Int = TimeTracer.measureAndLogTotalMillisecondsElapsedFor(logger, messageTemplate) {
            try {
                Thread.sleep(800)
            } catch (e: InterruptedException) {
                // ignore
            }
            2 + 3
        }
        Assertions.assertThat(sum).isEqualTo(5)
        Assertions.assertThat(logger.outContent).containsPattern(messageTemplate.format("8\\d\\d"))
    }

    @Test
    fun trackDurationOfSleepingThreadAction() {
        val logger: reactor.test.util.TestLogger = TestLogger()
        val messageTemplate: String = "my action took %s s"
        val sum: Int =
            TimeTracer.measureAndLogTotalDurationFor(logger, { dur: Duration -> messageTemplate.format(dur.seconds) }) {
                try {
                    Thread.sleep(1000)
                } catch (e: InterruptedException) {
                    // ignore
                }
                2 + 3
            }
        Assertions.assertThat(sum).isEqualTo(5)
        Assertions.assertThat(logger.outContent).containsPattern(messageTemplate.format("1"))
    }

    @Test
    fun trackTimeOfSleepingThreadActionOnLoggerExtensionTest() {
        val logger: reactor.test.util.TestLogger = TestLogger()
        val messageTemplate: String = "my action took %s ms"
        val sum: Int = logger.logTotalMillisecondsElapsedWith(messageTemplate) {
            try {
                Thread.sleep(800)
            } catch (e: InterruptedException) {
                // ignore
            }
            2 + 3
        }
        Assertions.assertThat(sum).isEqualTo(5)
        Assertions.assertThat(logger.outContent).containsPattern(messageTemplate.format("8\\d\\d"))
    }

    @Test
    fun trackTimeOfSleepingThreadErrorThrowingAction() {
        val logger: reactor.test.util.TestLogger = TestLogger()
        val messageTemplate: String = "my action took %s ms"
        val throwErrorFlag: Boolean = true
        Assertions.assertThatThrownBy {
            TimeTracer.measureAndLogTotalMillisecondsElapsedFor(logger, messageTemplate) {
                try {
                    Thread.sleep(800)
                } catch (e: InterruptedException) {
                    // ignore
                }
                if (throwErrorFlag) throw IllegalArgumentException("throw_error_flag on")
                2 + 3
            }
        }.isInstanceOf(IllegalArgumentException::class.java)
        Assertions.assertThat(logger.outContent).containsPattern(messageTemplate.format("8\\d\\d"))
    }

    @Test
    fun trackTimeOfSleepingThreadErrorThrowingActionOnLoggerExtensionTest() {
        val logger: reactor.test.util.TestLogger = TestLogger()
        val messageTemplate: String = "my action took %s ms"
        val throwErrorFlag: Boolean = true
        Assertions.assertThatThrownBy {
            logger.logTotalMillisecondsElapsedWith(messageTemplate) {
                try {
                    Thread.sleep(800)
                } catch (e: InterruptedException) {
                    // ignore
                }
                if (throwErrorFlag) throw IllegalArgumentException("throw_error_flag on")
                2 + 3
            }
        }.isInstanceOf(IllegalArgumentException::class.java)
        Assertions.assertThat(logger.outContent).containsPattern(messageTemplate.format("8\\d\\d"))
    }
}
