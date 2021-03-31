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

description = "${rootProject.description} (Bill of Materials with Dependencies)"

publishing {
    publications {
        configure(containerWithType(MavenPublication::class.java)) {
            nebulaDependencyManagement {
                from(components["javaPlatform"])
            }
        }
    }
}

configurations.all {
    // exclude group: "com.google.api", module: "gax-grpc"
    resolutionStrategy {
        // force 'org.eclipse.jetty:jetty-client:9.4.34.v20201102'
    }
}

javaPlatform {
    allowDependencies()
}

dependencies {
    api(platform(project(":graphql-dgs-platform")))
}

/* ----------------------------------------------------------- */
// Define Exclusions...
// Placeholder for external modules we should exclude, add "group:name", e.g. "com.google.code.guice:guice"
val ignoreExternalModules by extra(emptyList<String>())

/* ----------------------------------------------------------- */
afterEvaluate {
    // The passThroughRecommendations will contain the recommendations that are provided by other
    // BOMs used by this project. We will add them, but first we will also filter those that we
    // want to explicitly do not recommend, via the ignoreExternalModules.
    val passThroughRecommendations =
        rootProject.dependencyRecommendations.mavenBomProvider.recommendations
            .filterNot { it.key in ignoreExternalModules }
            .map { it.key to it.value }

    project.dependencies {
        constraints {
            passThroughRecommendations.forEach {
                logger.info("Adding ${it.first} as prefer constraint for ${it.second}.")
                api(it.first) {
                    version {
                        prefer(it.second)
                    }
                }
            }
        }
    }
}
