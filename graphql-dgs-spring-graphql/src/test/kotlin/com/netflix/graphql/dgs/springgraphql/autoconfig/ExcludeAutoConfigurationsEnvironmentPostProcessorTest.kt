package com.netflix.graphql.dgs.springgraphql.autoconfig

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.SpringApplication
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.env.getProperty

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
                mapOf(Pair("dgs.springgraphql.autoconfiguration.graphqlwebmvcsecurity.enabled", "true")),
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
            MapPropertySource("application-props", mapOf(Pair("dgs.springgraphql.autoconfiguration.graphqlobservation.enabled", "true"))),
        )

        ExcludeAutoConfigurationsEnvironmentPostProcessor().postProcessEnvironment(env, SpringApplication())
        assertThat(env.getProperty("spring.autoconfigure.exclude"))
            .contains("org.springframework.boot.graphql.autoconfigure.security.GraphQlWebMvcSecurityAutoConfiguration")
            .doesNotContain("org.springframework.boot.graphql.autoconfigure.observation.GraphQlObservationAutoConfiguration")
    }

    @Test
    fun `does not override existing excludes`() {
        val env = StandardEnvironment()
        env.propertySources.addLast(MapPropertySource("application-props", mapOf(Pair("spring.autoconfigure.exclude", "someexclude"))))

        ExcludeAutoConfigurationsEnvironmentPostProcessor().postProcessEnvironment(env, SpringApplication())
        assertThat(env.getProperty("spring.autoconfigure.exclude"))
            .contains(
                "someexclude",
                "org.springframework.boot.graphql.autoconfigure.observation.GraphQlObservationAutoConfiguration",
                "org.springframework.boot.graphql.autoconfigure.security.GraphQlWebMvcSecurityAutoConfiguration",
            )
    }

    @Test
    fun `array bindings via indexed properties should work`() {
        val env = StandardEnvironment()
        env.propertySources.addLast(MapPropertySource("application-Dev-props", mapOf(
            Pair("spring.autoconfigure.exclude[0]", "exclude-dev-1")
        )))
        env.propertySources.addLast(MapPropertySource("application-props", mapOf(
            Pair("spring.autoconfigure.exclude[0]", "exclude-1"),
            Pair("spring.autoconfigure.exclude[1]", "exclude-2")
        )))

        ExcludeAutoConfigurationsEnvironmentPostProcessor().postProcessEnvironment(env, SpringApplication())
        assertThat(env.getProperty<Array<String>>("spring.autoconfigure.exclude"))
            .containsExactly(
                "org.springframework.boot.graphql.autoconfigure.observation.GraphQlObservationAutoConfiguration",
                "org.springframework.boot.graphql.autoconfigure.security.GraphQlWebMvcSecurityAutoConfiguration",
                "exclude-dev-1"
            )
    }

    @Test
    fun `array bindings via string property should work`() {
        val env = StandardEnvironment()
        env.propertySources.addLast(MapPropertySource("application-Dev-props", mapOf(
            Pair("spring.autoconfigure.exclude", "exclude-dev-1,exclude-dev-2")
        )))
        env.propertySources.addLast(MapPropertySource("application-props", mapOf(
            Pair("spring.autoconfigure.exclude", "exclude-1")
        )))

        ExcludeAutoConfigurationsEnvironmentPostProcessor().postProcessEnvironment(env, SpringApplication())
        assertThat(env.getProperty<Array<String>>("spring.autoconfigure.exclude"))
            .containsExactly(
                "org.springframework.boot.graphql.autoconfigure.observation.GraphQlObservationAutoConfiguration",
                "org.springframework.boot.graphql.autoconfigure.security.GraphQlWebMvcSecurityAutoConfiguration",
                "exclude-dev-1",
                "exclude-dev-2",
            )
    }
}
