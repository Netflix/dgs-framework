package com.netflix.graphql.dgs.example.jackson3

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
open class Jackson3OnlyApp

fun main(args: Array<String>) {
    runApplication<Jackson3OnlyApp>(*args)
}

@DgsComponent
class HelloDataFetcher {
    @DgsQuery
    fun hello(
        @InputArgument name: String?,
    ): String = "hello, ${name ?: "stranger"}!"
}
