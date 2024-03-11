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
        // GraphQL Platform
        api("com.graphql-java:graphql-java") {
            version {
                strictly("[21.2, 21.3]")
                prefer("21.3")
                reject("[20.6, 19.5, 18.2]")
            }

        }
        api("com.graphql-java:java-dataloader") {
            version {
                strictly("[3.2.2]")
                prefer("3.2.2")
                reject("[3.2.1]")
            }

        }
        api("com.graphql-java:graphql-java-extended-scalars") {
            version {
                 strictly("[21.0]")
                 prefer("21.0")
                 reject("20.2")
            }
        }
        api("com.graphql-java:graphql-java-extended-validation") {
            version { strictly("21.0") }
        }
        api("com.apollographql.federation:federation-graphql-java-support") {
            version {
                strictly("[4.4.0]")
                prefer("4.4.0")
            }
        }
        // ---
        api("com.jayway.jsonpath:json-path") {
            version { require("2.9.0") }
        }
        api("io.projectreactor:reactor-core") {
            version { require("3.6.1") }
        }
        api("io.projectreactor:reactor-test"){
            version { require("3.6.1") }
        }
        // CVEs
        api("org.apache.logging.log4j:log4j-to-slf4j:2.23.1") {
            because("Refer to CVE-2021-44228; https://cve.mitre.org/cgi-bin/cvename.cgi?name=CVE-2021-44228")
         }
         api("org.apache.logging.log4j:log4j-api:2.23.0") {
            because("Refer to CVE-2021-44228; https://cve.mitre.org/cgi-bin/cvename.cgi?name=CVE-2021-44228")
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
                logger.info("Adding {} as constraint.", it)
                api(it)
            }
        }
    }
}
