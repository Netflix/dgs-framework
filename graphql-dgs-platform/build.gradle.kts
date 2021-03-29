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
//    id("netflix.bom-publish")
}

publishing {
    publications {
        create<MavenPublication>(project.name) {
            from(components["javaPlatform"])
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
    constraints {
        api("com.graphql-java:graphql-java:${Versions.GRAPHQL_JAVA}")
        api("com.apollographql.federation:federation-graphql-java-support:${Versions.GRAPHQL_JAVA_FEDERATION}")
        api("com.jayway.jsonpath:json-path:2.5.+")
        api("io.reactivex.rxjava3:rxjava:3.+")
        api("io.projectreactor:reactor-core:3.4.+")
        api("io.projectreactor:reactor-test:3.4.+")
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
            (subprojectRecommendations).forEach {
                logger.info("Adding ${it} as constraint.")
                api(it)
            }
        }
    }
}
