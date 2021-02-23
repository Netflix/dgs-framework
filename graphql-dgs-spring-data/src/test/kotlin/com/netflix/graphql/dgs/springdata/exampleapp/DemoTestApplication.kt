package com.netflix.graphql.dgs.springdata.exampleapp

import com.netflix.graphql.dgs.springdata.exampleapp.data.ShowEntityRepository
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.data.repository.core.support.RepositoryFactoryBeanSupport

@SpringBootApplication
@EnableJpaRepositories
@EnableJpaAuditing
open class DemoTestApplication {

	@Bean
	open fun showEntityRepositoryCheck(showEntityRepository: ShowEntityRepository): String{
		return showEntityRepository.toString()
	}
}

fun main(args: Array<String>) {
	runApplication<DemoTestApplication>(*args)
}
