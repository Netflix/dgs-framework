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
            "org.springframework.boot.graphql.autoconfigure.observation.GraphQlObservationAutoConfiguration",
            "org.springframework.boot.graphql.autoconfigure.security.GraphQlWebMvcSecurityAutoConfiguration",
        )
    }

    @Test
    fun `Security autoconfig can be enabled`() {
        val env = StandardEnvironment()
        env.propertySources.addLast(
            MapPropertySource(
                "application-props",
                mapOf("dgs.springgraphql.autoconfiguration.graphqlwebmvcsecurity.enabled" to "true"),
            ),
        )

        ExcludeAutoConfigurationsEnvironmentPostProcessor().postProcessEnvironment(env, SpringApplication())
        assertThat(env.getProperty("spring.autoconfigure.exclude"))
            .contains("org.springframework.boot.graphql.autoconfigure.observation.GraphQlObservationAutoConfiguration")
            .doesNotContain("org.springframework.boot.graphql.autoconfigure.security.GraphQlWebMvcSecurityAutoConfiguration")
    }

    @Test
    fun `Observation autoconfig can be enabled`() {
        val env = StandardEnvironment()
        env.propertySources.addLast(
            MapPropertySource("application-props", mapOf("dgs.springgraphql.autoconfiguration.graphqlobservation.enabled" to "true")),
        )

        ExcludeAutoConfigurationsEnvironmentPostProcessor().postProcessEnvironment(env, SpringApplication())
        assertThat(env.getProperty("spring.autoconfigure.exclude"))
            .contains("org.springframework.boot.graphql.autoconfigure.security.GraphQlWebMvcSecurityAutoConfiguration")
            .doesNotContain("org.springframework.boot.graphql.autoconfigure.observation.GraphQlObservationAutoConfiguration")
    }

    @Test
    fun `does not override existing excludes`() {
        val env = StandardEnvironment()
        env.propertySources.addLast(MapPropertySource("application-props", mapOf("spring.autoconfigure.exclude" to "someexclude")))

        ExcludeAutoConfigurationsEnvironmentPostProcessor().postProcessEnvironment(env, SpringApplication())
        assertThat(env.getProperty("spring.autoconfigure.exclude"))
            .contains(
                "someexclude",
                "org.springframework.boot.graphql.autoconfigure.observation.GraphQlObservationAutoConfiguration",
                "org.springframework.boot.graphql.autoconfigure.security.GraphQlWebMvcSecurityAutoConfiguration",
            )
    }

    @Test
    fun `does not reintroduce overridden excludes in test properties`() {
        val env = StandardEnvironment()
        env.propertySources.addLast(MapPropertySource("application-props", mapOf("spring.autoconfigure.exclude" to "someexclude")))
        env.propertySources.addLast(
            MapPropertySource("Inlined Test Properties", mapOf("spring.autoconfigure.exclude" to "someotherexclude")),
        )

        ExcludeAutoConfigurationsEnvironmentPostProcessor().postProcessEnvironment(env, SpringApplication())
        assertThat(env.getProperty("spring.autoconfigure.exclude"))
            .contains(
                "someotherexclude",
                "org.springframework.boot.graphql.autoconfigure.observation.GraphQlObservationAutoConfiguration",
                "org.springframework.boot.graphql.autoconfigure.security.GraphQlWebMvcSecurityAutoConfiguration",
            )

        assertThat(env.getProperty("spring.autoconfigure.exclude"))
            .doesNotContain("someexclude")
    }
}
