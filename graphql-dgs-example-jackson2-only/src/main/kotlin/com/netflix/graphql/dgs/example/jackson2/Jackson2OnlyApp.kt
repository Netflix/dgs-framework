package com.netflix.graphql.dgs.example.jackson2

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
open class Jackson2OnlyApp

fun main(args: Array<String>) {
    runApplication<Jackson2OnlyApp>(*args)
}

@DgsComponent
class HelloDataFetcher {
    @DgsQuery
    fun hello(
        @InputArgument name: String?,
    ): String = "hello, ${name ?: "stranger"}!"
}
