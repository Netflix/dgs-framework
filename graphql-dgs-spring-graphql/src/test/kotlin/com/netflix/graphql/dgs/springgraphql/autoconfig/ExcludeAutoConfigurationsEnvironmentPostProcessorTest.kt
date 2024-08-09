package com.netflix.graphql.dgs.springgraphql.autoconfig

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.SpringApplication
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment

class ExcludeAutoConfigurationsEnvironmentPostProcessorTest {
    @Test
    fun `disables unwanted auto-configurations`() {
        val env = StandardEnvironment()
        ExcludeAutoConfigurationsEnvironmentPostProcessor().postProcessEnvironment(env, SpringApplication())
        assertThat(
            env.getProperty("spring.autoconfigure.exclude"),
        ).contains(
            "org.springframework.boot.actuate.autoconfigure.observation.graphql.GraphQlObservationAutoConfiguration",
            "org.springframework.boot.autoconfigure.graphql.security.GraphQlWebMvcSecurityAutoConfiguration",
        )
    }

    @Test
    fun `Security autoconfig can be enabled`() {
        val env = StandardEnvironment()
        env.propertySources.addLast(
            MapPropertySource(
                "application-props",
                mapOf(Pair("dgs.springgraphql.autoconfiguration.graphqlwebmvcsecurity.enabled", "true")),
            ),
        )

        ExcludeAutoConfigurationsEnvironmentPostProcessor().postProcessEnvironment(env, SpringApplication())
        assertThat(env.getProperty("spring.autoconfigure.exclude"))
            .contains("org.springframework.boot.actuate.autoconfigure.observation.graphql.GraphQlObservationAutoConfiguration")
            .doesNotContain("org.springframework.boot.autoconfigure.graphql.security.GraphQlWebMvcSecurityAutoConfiguration")
    }

    @Test
    fun `Observation autoconfig can be enabled`() {
        val env = StandardEnvironment()
        env.propertySources.addLast(
            MapPropertySource("application-props", mapOf(Pair("dgs.springgraphql.autoconfiguration.graphqlobservation.enabled", "true"))),
        )

        ExcludeAutoConfigurationsEnvironmentPostProcessor().postProcessEnvironment(env, SpringApplication())
        assertThat(env.getProperty("spring.autoconfigure.exclude"))
            .contains("org.springframework.boot.autoconfigure.graphql.security.GraphQlWebMvcSecurityAutoConfiguration")
            .doesNotContain("org.springframework.boot.actuate.autoconfigure.observation.graphql.GraphQlObservationAutoConfiguration")
    }

    @Test
    fun `does not override existing excludes`() {
        val env = StandardEnvironment()
        env.propertySources.addLast(MapPropertySource("application-props", mapOf(Pair("spring.autoconfigure.exclude", "someexclude"))))

        ExcludeAutoConfigurationsEnvironmentPostProcessor().postProcessEnvironment(env, SpringApplication())
        assertThat(env.getProperty("spring.autoconfigure.exclude"))
            .contains(
                "someexclude",
                "org.springframework.boot.actuate.autoconfigure.observation.graphql.GraphQlObservationAutoConfiguration",
                "org.springframework.boot.autoconfigure.graphql.security.GraphQlWebMvcSecurityAutoConfiguration",
            )
    }
}
