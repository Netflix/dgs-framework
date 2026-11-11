package com.netflix.graphql.dgs.example.jacksonboth

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
open class JacksonBothApp

fun main(args: Array<String>) {
    runApplication<JacksonBothApp>(*args)
}

@DgsComponent
class HelloDataFetcher {
    @DgsQuery
    fun hello(
        @InputArgument name: String?,
    ): String = "hello, ${name ?: "stranger"}!"
}
