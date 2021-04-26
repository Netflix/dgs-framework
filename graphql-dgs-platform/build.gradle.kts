/*
 * Copyright 2021 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

plugins {
    `java-platform`
    `maven-publish`
}

publishing {
    publications {
        configure(containerWithType(MavenPublication::class.java)) {
            nebulaDependencyManagement {
                from(components["javaPlatform"])
            }
        }
    }
}

description = "${rootProject.description} (Bill of Materials)"

// Evaluation dependencies.
rootProject.subprojects
    .sorted()
    .filterNot { it == project }
    .forEach {
        logger.info("Declaring an evaluation dependency on ${it.path}.")
        evaluationDependsOn(it.path)
    }

dependencies {
    // The following constraints leverage the _rich versioning_ exposed by Gradle,
    // this will be published as Maven Metadata.
    // For more information at https://docs.gradle.org/current/userguide/rich_versions.html
    constraints {
        api("com.graphql-java:graphql-java") {
            version { require(Versions.GRAPHQL_JAVA) }
        }
        api("com.graphql-java:graphql-java-extended-scalars") {
            version { require(Versions.GRAPHQL_JAVA_EXTENDED_SCALARS) }
        }
        api("com.apollographql.federation:federation-graphql-java-support") {
            version { require(Versions.GRAPHQL_JAVA_FEDERATION) }
        }
        api("com.jayway.jsonpath:json-path") {
            version { require("[2.5,)") }
        }
        api("io.projectreactor:reactor-core") {
            version { require("[3.4,)") }
        }
        api("io.projectreactor:reactor-test"){
            version { require("[3.4,)") }
        }
    }
}

/* ----------------------------------------------------------- */
// Define Exclusions...

// The following internal modules will be excluded from the BOM
val ignoreInternalModules by extra(
    listOf(
        project(":graphql-dgs-example-java"),
        project(":graphql-dgs-platform-dependencies")
    )
)

/* ----------------------------------------------------------- */
afterEvaluate {
    val subprojectRecommendations =
        rootProject
            .subprojects
            .filterNot { it == project || it in ignoreInternalModules }

    project.dependencies {
        constraints {
            subprojectRecommendations.forEach {
                logger.info("Adding ${it} as constraint.")
                api(it)

            }
        }
    }
}
