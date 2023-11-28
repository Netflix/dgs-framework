package com.netflix.graphql.dgs.internal

import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.core.task.AsyncTaskExecutor
import reactor.core.publisher.Mono
import java.util.concurrent.CompletableFuture
import java.util.function.Function
import kotlin.reflect.jvm.ExperimentalReflectionOnLambdas
import kotlin.reflect.jvm.reflect

@OptIn(ExperimentalReflectionOnLambdas::class)
@ExtendWith(MockKExtension::class)
class CompletableFutureWrapperTest {
    @MockK(relaxUnitFun = true)
    lateinit var mockTaskExecutor: AsyncTaskExecutor

    @Test
    fun `If no taskExecutor is set, no wrapping should happen`() {
        val completableFutureWrapper = CompletableFutureWrapper(null)

        // Check Kotlin method
        assertThat(completableFutureWrapper.shouldWrapInCompletableFuture(fun(): String { return "hello" }.reflect()!!)).isFalse()

        // Check Java method
        val stringMethod = String::class.java.getMethod("toString")
        assertThat(completableFutureWrapper.shouldWrapInCompletableFuture(stringMethod)).isFalse()
    }

    @Test
    fun `A Kotlin String function should be wrapped`() {
        val completableFutureWrapper = CompletableFutureWrapper(VirtualThreadTaskExecutor())
        assertThat(completableFutureWrapper.shouldWrapInCompletableFuture(fun(): String { return "hello" }.reflect()!!)).isTrue()
    }

    @Test
    fun `A Kotlin CompletableFuture function should not be wrapped`() {
        val completableFutureWrapper = CompletableFutureWrapper(VirtualThreadTaskExecutor())
        assertThat(completableFutureWrapper.shouldWrapInCompletableFuture(fun(): CompletableFuture<String> { return CompletableFuture() }.reflect()!!)).isFalse()
    }

    @Test
    fun `A Kotlin Mono function should not be wrapped`() {
        val completableFutureWrapper = CompletableFutureWrapper(VirtualThreadTaskExecutor())
        assertThat(completableFutureWrapper.shouldWrapInCompletableFuture(fun(): Mono<String> { return Mono.just("hi") }.reflect()!!)).isFalse()
    }

    @Test
    fun `A Java String method should be wrapped`() {
        val completableFutureWrapper = CompletableFutureWrapper(VirtualThreadTaskExecutor())
        val stringMethod = String::class.java.getMethod("toString")
        assertThat(completableFutureWrapper.shouldWrapInCompletableFuture(stringMethod)).isTrue()
    }

    @Test
    fun `A Java CompletableFuture method should not be wrapped`() {
        val completableFutureWrapper = CompletableFutureWrapper(VirtualThreadTaskExecutor())
        val cfMethod = CompletableFuture::class.java.getMethod("thenApplyAsync", Function::class.java)
        assertThat(completableFutureWrapper.shouldWrapInCompletableFuture(cfMethod)).isFalse()
    }

    @Test
    fun `A Java Mono method should not be wrapped`() {
        val completableFutureWrapper = CompletableFutureWrapper(VirtualThreadTaskExecutor())
        val monoMethod = Mono::class.java.getMethod("empty")
        assertThat(completableFutureWrapper.shouldWrapInCompletableFuture(monoMethod)).isFalse()
    }

    @Test
    fun `A method should successfully get wrapped`() {
        val completableFutureWrapper = CompletableFutureWrapper(mockTaskExecutor)
        val wrapped = completableFutureWrapper.wrapInCompletableFuture { fun(): String { return "hello" } }
        assertThat(wrapped).isInstanceOf(CompletableFuture::class.java)
    }
}
