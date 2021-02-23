package com.netflix.graphql.dgs.springdata.exampleapp

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
open class DemoTestApplication

fun main(args: Array<String>) {
	runApplication<DemoTestApplication>(*args)
}
