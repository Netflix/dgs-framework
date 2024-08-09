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

@ExtendWith(MockKExtension::class)
class CompletableFutureWrapperTest {
    @MockK(relaxUnitFun = true)
    lateinit var mockTaskExecutor: AsyncTaskExecutor

    @Test
    fun `If no taskExecutor is set, no wrapping should happen`() {
        val completableFutureWrapper = CompletableFutureWrapper(null)
        // Check Kotlin method
        assertThat(completableFutureWrapper.shouldWrapInCompletableFuture(this::kotlinMethod)).isFalse()

        // Check Java method
        val stringMethod = String::class.java.getMethod("toString")
        assertThat(completableFutureWrapper.shouldWrapInCompletableFuture(stringMethod)).isFalse()
    }

    @Test
    fun `A Kotlin String function should be wrapped`() {
        val completableFutureWrapper = CompletableFutureWrapper(VirtualThreadTaskExecutor(null))
        assertThat(completableFutureWrapper.shouldWrapInCompletableFuture(this::kotlinMethod)).isTrue()
    }

    @Test
    fun `A Kotlin CompletableFuture function should not be wrapped`() {
        val completableFutureWrapper = CompletableFutureWrapper(VirtualThreadTaskExecutor(null))
        assertThat(completableFutureWrapper.shouldWrapInCompletableFuture(this::kotlinCompletableFutureMethod)).isFalse()
    }

    @Test
    fun `A Kotlin Mono function should not be wrapped`() {
        val completableFutureWrapper = CompletableFutureWrapper(VirtualThreadTaskExecutor(null))
        assertThat(completableFutureWrapper.shouldWrapInCompletableFuture(this::kotlinMonoMethod)).isFalse()
    }

    @Test
    fun `A Java String method should be wrapped`() {
        val completableFutureWrapper = CompletableFutureWrapper(VirtualThreadTaskExecutor(null))
        val stringMethod = String::class.java.getMethod("toString")
        assertThat(completableFutureWrapper.shouldWrapInCompletableFuture(stringMethod)).isTrue()
    }

    @Test
    fun `A Java CompletableFuture method should not be wrapped`() {
        val completableFutureWrapper = CompletableFutureWrapper(VirtualThreadTaskExecutor(null))
        val cfMethod = CompletableFuture::class.java.getMethod("thenApplyAsync", Function::class.java)
        assertThat(completableFutureWrapper.shouldWrapInCompletableFuture(cfMethod)).isFalse()
    }

    @Test
    fun `A Java Mono method should not be wrapped`() {
        val completableFutureWrapper = CompletableFutureWrapper(VirtualThreadTaskExecutor(null))
        val monoMethod = Mono::class.java.getMethod("empty")
        assertThat(completableFutureWrapper.shouldWrapInCompletableFuture(monoMethod)).isFalse()
    }

    @Test
    fun `A method should successfully get wrapped`() {
        val completableFutureWrapper = CompletableFutureWrapper(mockTaskExecutor)
        val wrapped = completableFutureWrapper.wrapInCompletableFuture(fun(): String = "hello")
        assertThat(wrapped).isInstanceOf(CompletableFuture::class.java)
    }

    private fun kotlinMonoMethod(): Mono<String> = Mono.just("hello")

    private fun kotlinCompletableFutureMethod(): CompletableFuture<String> = CompletableFuture.completedFuture("hello")

    private fun kotlinMethod(): String = "hello"
}
