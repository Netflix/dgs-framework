package com.netflix.graphql.dgs.springgraphql.autoconfig

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource

@SpringBootTest(
    classes = [
        ExcludeAutoConfigurationsEnvironmentPostProcessorOverrideTest.TestApp::class,
    ],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
)
@TestPropertySource(properties = ["spring.autoconfigure.exclude=someotherexclude"])
@ActiveProfiles("PropertiesOverrideTest")
class ExcludeAutoConfigurationsEnvironmentPostProcessorOverrideTest {
    @Value($$"${spring.autoconfigure.exclude}")
    private var excludesString: String? = null

    @Value($$"${spring.autoconfigure.exclude}")
    private var excludesArray: Array<String>? = null

    @Test
    fun `value from test properties source should be augmented`() {
        assertThat(excludesString).contains(EXPECTED_AUTOCONFIGURATION_EXCLUSIONS)
        assertThat(excludesString).doesNotContain("someexclude")
        assertThat(excludesArray).containsExactlyInAnyOrderElementsOf(EXPECTED_AUTOCONFIGURATION_EXCLUSIONS)
    }

    @SpringBootApplication
    internal open class TestApp

    private companion object {
        private val EXPECTED_AUTOCONFIGURATION_EXCLUSIONS =
            listOf(
                "someotherexclude",
                "org.springframework.boot.graphql.autoconfigure.observation.GraphQlObservationAutoConfiguration",
                "org.springframework.boot.graphql.autoconfigure.security.GraphQlWebMvcSecurityAutoConfiguration",
            )
    }
}
