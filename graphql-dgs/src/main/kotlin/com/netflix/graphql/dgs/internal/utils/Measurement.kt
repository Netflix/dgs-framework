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

package com.netflix.graphql.dgs.internal.utils

import org.slf4j.event.Level
import java.time.Duration

/**
 * Type that represents something that can be measured from a start to a finish via its {@link MeasurementState}
 * @param <S> - Start Value: Value created just before a given action is run
 * @param <F> - Finish Value: Value calculated using the start value as input just after a given action is run
 */
interface Measurement<S, F> {

    /**
     * Type that represents the state of a {@link Measurement}:
     * - before it has started,
     * - after its action has been performed and resulted in error,
     * - or after its action has been performed and yielded a successful result value
     * @param <S> - Start Value: Value created just before a given action is run
     * @param <A> - Action Result Value: Value that is the result of running the action function
     * @param <F> - Finish Value: Value calculated using the start value as input just after a given action has been run
     */
    sealed interface MeasurementState<S, A, F> {

        companion object {

            @JvmStatic
            fun <S, A, F> ofBeginMeasurement(
                onStart: () -> S,
                action: () -> A,
                onFinish: (S) -> F
            ): MeasurementState<S, A, F> {
                return BeginMeasurementState<S, A, F>(onStart, action, onFinish)
            }

            @JvmStatic
            fun <S, A, F> ofActionErrored(finishValue: F, error: Throwable): MeasurementState<S, A, F> {
                return ActionErroredMeasurementState<S, A, F>(finishValue, error)
            }

            @JvmStatic
            fun <S, A, F> ofActionSuccessful(finishValue: F, actionResult: A): MeasurementState<S, A, F> {
                return ActionSuccessfulMeasurementState<S, A, F>(finishValue, actionResult)
            }

            data class BeginMeasurementState<S, A, F>(
                val onStart: () -> S,
                val action: () -> A,
                val onFinish: (S) -> F
            ) : MeasurementState<S, A, F> {
                override fun <R> fold(
                    start: (() -> S, () -> A, (S) -> F) -> R,
                    error: (F, Throwable) -> R,
                    finish: (F, A) -> R
                ): R {
                    return start.invoke(onStart, action, onFinish)
                }
            }

            data class ActionErroredMeasurementState<S, A, F>(val finishValue: F, val throwable: Throwable) :
                MeasurementState<S, A, F> {
                override fun <R> fold(
                    start: (() -> S, () -> A, (S) -> F) -> R,
                    error: (F, Throwable) -> R,
                    finish: (F, A) -> R
                ): R {
                    return error.invoke(finishValue, throwable)
                }
            }

            data class ActionSuccessfulMeasurementState<S, A, F>(val finishValue: F, val result: A) :
                MeasurementState<S, A, F> {
                override fun <R> fold(
                    start: (() -> S, () -> A, (S) -> F) -> R,
                    error: (F, Throwable) -> R,
                    finish: (F, A) -> R
                ): R {
                    return finish.invoke(finishValue, result)
                }
            }
        }

        /**
         * Has the state of the measurement been transition from the begin state to errored or successful
         * action result states
         */
        fun hasRun(): Boolean {
            return fold({ _, _, _ -> false }, { _, _ -> true }, { _, _ -> true })
        }

        fun transition(): MeasurementState<S, A, F> {
            return fold({ onStart: () -> S, action: () -> A, onFinish: (S) -> F ->
                val startVal = onStart.invoke()
                try {
                    val actionResult: A = action.invoke()
                    ofActionSuccessful<S, A, F>(onFinish.invoke(startVal), actionResult)
                } catch (t: Throwable) {
                    ofActionErrored<S, A, F>(onFinish.invoke(startVal), t)
                }
            }, { finishVal: F, throwable: Throwable ->
                ofActionErrored(finishVal, throwable)
            }, { finishVal: F, actionResult: A ->
                ofActionSuccessful(finishVal, actionResult)
            })
        }

        fun isError(): Boolean {
            return fold({ _, _, _ -> false }, { _, _ -> true }, { _, _ -> false })
        }

        fun isSuccess(): Boolean {
            return fold({ _, _, _ -> false }, { _, _ -> false }, { _, _ -> true })
        }

        fun getError(): Throwable? {
            return fold({ _, _, _ -> null }, { _, thr -> thr }, { _, _ -> null })
        }

        fun getActionResult(): A? {
            return fold({ _, _, _ -> null }, { _, _ -> null }, { _, result -> result })
        }

        fun getFinalMeasurementValue(): F? {
            return fold({ _, _, _ -> null }, { f, _ -> f }, { f, _ -> f })
        }

        fun getActionResultOrElseThrow(): A {
            return when {
                isSuccess() -> {
                    getActionResult()!!
                }
                isError() -> {
                    throw getError()!!
                }
                else -> {
                    val afterRun = transition()
                    when {
                        afterRun.isSuccess() -> afterRun.getActionResult()!!
                        else -> throw afterRun.getError()!!
                    }
                }
            }
        }

        fun <R> mapActionResult(function: (A) -> R): MeasurementState<S, R, F> {
            return fold(
                { start: () -> S, action: () -> A, finish: (S) -> F ->
                    ofBeginMeasurement(
                        start, { -> function.invoke(action.invoke()) }, finish
                    )
                },
                { finish: F, throwable: Throwable -> ofActionErrored(finish, throwable) },
                { finish: F, actionResult: A -> ofActionSuccessful(finish, function.invoke(actionResult)) }
            )
        }

        fun <R> mapFinalMeasurementValue(function: (F) -> R): MeasurementState<S, A, R> {
            return fold({ start: () -> S, action: () -> A, finish: (S) -> F ->
                ofBeginMeasurement(
                    onStart = start,
                    action = action,
                    onFinish = { s: S -> function.invoke(finish.invoke(s)) }
                )
            }, { finishValue: F, throwable: Throwable ->
                ofActionErrored(finishValue = function.invoke(finishValue), error = throwable)
            }, { finishValue: F, actionResult: A ->
                ofActionSuccessful(finishValue = function.invoke(finishValue), actionResult = actionResult)
            })
        }

        /**
         * Use existing action result if present and successful or else, run and calculate it
         * Use this action result value to change the measurement state, supplying a different value
         * or erroring out as desired
         */
        fun <R> flatMapSuccessfulActionResult(function: (F, A) -> MeasurementState<S, R, F>): MeasurementState<S, R, F> {
            return fold({ start: () -> S, action: () -> A, finish: (S) -> F ->
                val startValue: S = start.invoke()
                try {
                    val actionResult: A = action.invoke()
                    val finishValue = finish.invoke(startValue)
                    function.invoke(finishValue, actionResult)
                } catch (t: Throwable) {
                    ofActionErrored<S, R, F>(finish.invoke(startValue), t)
                }
            }, { finishValue: F, throwable: Throwable ->
                ofActionErrored<S, R, F>(finishValue = finishValue, error = throwable)
            }, { finishValue: F, actionResult: A ->
                function.invoke(finishValue, actionResult)
            })
        }

        /**
         * Use existing value of measurement if present or else, run and calculate it
         * Use this measured value to change the measurement state, supplying a different value
         * or erroring out as desired
         */
        fun <R> flatMapFinalMeasurementValue(function: (F, A?, Throwable?) -> MeasurementState<S, A, R>): MeasurementState<S, A, R> {
            return fold({ start: () -> S, action: () -> A, finish: (S) -> F ->
                val startValue: S = start.invoke()
                try {
                    val actionResult: A = action.invoke()
                    val finishValue = finish.invoke(startValue)
                    function.invoke(finishValue, actionResult, null)
                } catch (t: Throwable) {
                    function.invoke(finish.invoke(startValue), null, t)
                }
            }, { finishValue: F, throwable: Throwable ->
                function.invoke(finishValue, null, throwable)
            }, { finishValue: F, actionResult: A ->
                function.invoke(finishValue, actionResult, null)
            })
        }

        fun actionResultOrElseGet(other: () -> A): A {
            return getActionResult() ?: other.invoke()
        }

        fun errorOrElseGet(other: () -> Throwable): Throwable {
            return getError() ?: other.invoke()
        }

        fun finalMeasurementValueOrElseGet(other: () -> F): F {
            return getFinalMeasurementValue() ?: other.invoke()
        }

        fun <R> fold(
            start: (() -> S, () -> A, (S) -> F) -> R,
            error: (F, Throwable) -> R,
            finish: (F, A) -> R
        ): R
    }

    companion object {

        @JvmStatic
        fun <S, F> createMeasurementType(onStart: () -> S, onFinish: (S) -> F): Measurement<S, F> {
            return DefaultMeasurement<S, F>(onStart, onFinish)
        }

        @JvmStatic
        fun measureTotalMillisecondsElapsed(): Measurement<Long, Long> {
            return createMeasurementType(onStart = { System.currentTimeMillis() }, onFinish = { startVal: Long ->
                System.currentTimeMillis() - startVal
            })
        }

        @JvmStatic
        fun measureTotalDurationElapsed(): Measurement<Long, Duration> {
            return createMeasurementType(onStart = { System.currentTimeMillis() }, onFinish = { startVal: Long ->
                Duration.ofMillis(System.currentTimeMillis()).minusMillis(startVal)
            })
        }

        @JvmStatic
        @JvmOverloads
        fun measureAndLogTotalMillisecondsElapsed(
            logger: org.slf4j.Logger,
            level: Level = Level.DEBUG,
            messageForTotalTimeMs: (Long) -> String = { tt: Long -> "total_time: $tt ms" }
        ): Measurement<Long, Long> {
            return createMeasurementType(onStart = { System.currentTimeMillis() }, onFinish = { startVal: Long ->
                val totalTime: Long = System.currentTimeMillis() - startVal
                when (level) {
                    Level.ERROR -> logger.error(messageForTotalTimeMs.invoke(totalTime))
                    Level.WARN -> logger.warn(messageForTotalTimeMs.invoke(totalTime))
                    Level.INFO -> logger.info(messageForTotalTimeMs.invoke(totalTime))
                    Level.DEBUG -> logger.debug(messageForTotalTimeMs.invoke(totalTime))
                    Level.TRACE -> logger.trace(messageForTotalTimeMs.invoke(totalTime))
                }
                totalTime
            })
        }

        @JvmStatic
        @JvmOverloads
        fun measureAndLogDurationOf(
            logger: org.slf4j.Logger,
            level: Level = Level.DEBUG,
            messageForTotalDuration: (Duration) -> String = { tt: Duration -> "total_duration: ${tt.toMillis()} ms" }
        ): Measurement<Long, Duration> {
            return createMeasurementType(onStart = { System.currentTimeMillis() }, onFinish = { startVal: Long ->
                val totalTime: Duration = Duration.ofMillis(System.currentTimeMillis()).minusMillis(startVal)
                when (level) {
                    Level.DEBUG -> logger.debug(messageForTotalDuration.invoke(totalTime))
                    Level.TRACE -> logger.trace(messageForTotalDuration.invoke(totalTime))
                    Level.ERROR -> logger.error(messageForTotalDuration.invoke(totalTime))
                    Level.WARN -> logger.warn(messageForTotalDuration.invoke(totalTime))
                    Level.INFO -> logger.info(messageForTotalDuration.invoke(totalTime))
                }
                totalTime
            })
        }

        @JvmStatic
        @JvmOverloads
        fun measureAndLogTotalMillisecondsElapsed(
            logger: reactor.util.Logger,
            level: Level = Level.DEBUG,
            messageForTotalTimeMs: (Long) -> String = { tt: Long -> "total_time: $tt ms" }
        ): Measurement<Long, Long> {
            return createMeasurementType(onStart = { System.currentTimeMillis() }, onFinish = { startVal: Long ->
                val totalTime: Long = System.currentTimeMillis() - startVal
                when (level) {
                    Level.ERROR -> logger.error(messageForTotalTimeMs.invoke(totalTime))
                    Level.WARN -> logger.warn(messageForTotalTimeMs.invoke(totalTime))
                    Level.INFO -> logger.info(messageForTotalTimeMs.invoke(totalTime))
                    Level.DEBUG -> logger.debug(messageForTotalTimeMs.invoke(totalTime))
                    Level.TRACE -> logger.trace(messageForTotalTimeMs.invoke(totalTime))
                }
                totalTime
            })
        }

        @JvmStatic
        @JvmOverloads
        fun measureAndLogDurationOf(
            logger: reactor.util.Logger,
            level: Level = Level.DEBUG,
            messageForTotalDuration: (Duration) -> String = { tt: Duration -> "total_duration: ${tt.toMillis()} ms" }
        ): Measurement<Long, Duration> {
            return createMeasurementType(onStart = { System.currentTimeMillis() }, onFinish = { startVal: Long ->
                val totalTime: Duration = Duration.ofMillis(System.currentTimeMillis()).minusMillis(startVal)
                when (level) {
                    Level.DEBUG -> logger.debug(messageForTotalDuration.invoke(totalTime))
                    Level.TRACE -> logger.trace(messageForTotalDuration.invoke(totalTime))
                    Level.ERROR -> logger.error(messageForTotalDuration.invoke(totalTime))
                    Level.WARN -> logger.warn(messageForTotalDuration.invoke(totalTime))
                    Level.INFO -> logger.info(messageForTotalDuration.invoke(totalTime))
                }
                totalTime
            })
        }

        internal data class DefaultMeasurement<S, F>(
            override val startingAction: () -> S,
            override val finishingAction: (S) -> F
        ) : Measurement<S, F>
    }

    /**
     * Supplier/Producer to be called just prior to a given action function input invocation
     */
    val startingAction: () -> S

    /**
     * Function to be called with the output value of the starting value supplier just after
     * the invocation of a given action function
     */
    val finishingAction: (S) -> F

    /**
     * Transitions measurement state from begin state to either action errored or action successful with measurement
     * executing starting and finishing actions accordingly
     */
    fun <R> run(action: () -> R): MeasurementState<S, R, F> {
        return MeasurementState.ofBeginMeasurement<S, R, F>(startingAction, action, finishingAction).transition()
    }

    fun <R, U> run(
        action: () -> R,
        successfulFollowupAction: (F, R) -> U,
        finalMeasurementsCombiner: (F, F) -> F
    ): MeasurementState<S, U, F> {
        return MeasurementState.ofBeginMeasurement<S, R, F>(startingAction, action, finishingAction).transition()
            .flatMapSuccessfulActionResult { finishVal1: F, actionResult: R ->
                MeasurementState.ofBeginMeasurement(
                    startingAction, { -> successfulFollowupAction.invoke(finishVal1, actionResult) }, finishingAction
                ).transition().mapFinalMeasurementValue { finishVal2: F ->
                    finalMeasurementsCombiner.invoke(
                        finishVal1, finishVal2
                    )
                }
            }
    }
}
