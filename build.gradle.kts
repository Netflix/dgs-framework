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

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    repositories {
        mavenCentral()
    }
}

group = "com.netflix.graphql.dgs"

plugins {
    `java-library`
    id("nebula.netflixoss") version "10.4.0"
    id("nebula.dependency-recommender") version "11.0.0"
    id("org.jmailen.kotlinter") version "3.6.0"
    kotlin("jvm") version Versions.KOTLIN_VERSION
    kotlin("kapt") version Versions.KOTLIN_VERSION
    idea
    eclipse
}

allprojects {
    group = "com.netflix.graphql.dgs"
    repositories {
        mavenCentral()
    }

    apply(plugin = "nebula.netflixoss")
    apply(plugin = "nebula.dependency-recommender")

    // We are attempting to define the versions of the artifacts closest to the
    // place they are referenced such that dependabot can easily pick them up
    // and suggest an upgrade. The only exception currently are those defined
    // in buildSrc, most likley because the variables are used in plugins as well
    // as dependencies. e.g. KOTLIN_VERSION
    extra["sb.version"] = "2.3.12.RELEASE"
    val SB_VERSION = extra["sb.version"] as String

    dependencyRecommendations {
        mavenBom(mapOf("module" to "org.jetbrains.kotlin:kotlin-bom:${Versions.KOTLIN_VERSION}"))
        mavenBom(mapOf("module" to "org.springframework:spring-framework-bom:5.3.13"))
        mavenBom(mapOf("module" to "org.springframework.boot:spring-boot-dependencies:${SB_VERSION}"))
        mavenBom(mapOf("module" to "org.springframework.security:spring-security-bom:5.3.12.RELEASE"))
        mavenBom(mapOf("module" to "org.springframework.cloud:spring-cloud-dependencies:Hoxton.SR12"))
        mavenBom(mapOf("module" to "com.fasterxml.jackson:jackson-bom:2.12.5"))
    }
}

val internalBomModules by extra(
    listOf(
        project(":graphql-dgs-platform"),
        project(":graphql-dgs-platform-dependencies")
    )
)

configure(subprojects.filterNot { it in internalBomModules }) {

    apply {
        plugin("java-library")
        plugin("kotlin")
        plugin("kotlin-kapt")
        plugin("org.jmailen.kotlinter")
    }

    /**
     * Remove once the following ticket is closed:
     *  Kotlin-JVM: runtimeOnlyDependenciesMetadata, implementationDependenciesMetadata should be marked with isCanBeResolved=false
     *  https://youtrack.jetbrains.com/issue/KT-34394
     */
    tasks.named("generateLock") {
        doFirst {
            project.configurations.filter { it.name.contains("DependenciesMetadata") }.forEach {
                it.isCanBeResolved = false
            }
        }
    }

    val SB_VERSION = extra["sb.version"] as String
    dependencies {
        // Apply the BOM to applicable subprojects.
        api(platform(project(":graphql-dgs-platform")))
        // Speed up processing of AutoConfig's produced by Spring Boot
        annotationProcessor("org.springframework.boot:spring-boot-autoconfigure-processor")
        // Produce Config Metadata for properties used in Spring Boot
        annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
        // Speed up processing of AutoConfig's produced by Spring Boot for Kotlin
        kapt("org.springframework.boot:spring-boot-autoconfigure-processor:${SB_VERSION}")
        // Produce Config Metadata for properties used in Spring Boot for Kotlin
        kapt("org.springframework.boot:spring-boot-configuration-processor:${SB_VERSION}")

        testImplementation("org.springframework.boot:spring-boot-starter-test") {
            exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
        }
        testImplementation("io.mockk:mockk:1.12.1")
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kapt {
        arguments {
            arg(
                "org.springframework.boot.configurationprocessor.additionalMetadataLocations",
                "$projectDir/src/main/resources"
            )
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs + "-parameters"
    }

    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions {
            freeCompilerArgs += "-Xjvm-default=enable"
            jvmTarget = "1.8"
        }
    }

    tasks {
        test {
            useJUnitPlatform()
        }
    }

    kotlinter {
        indentSize = 4
        reporters = arrayOf("checkstyle", "plain")
        experimentalRules = false
        disabledRules = arrayOf("no-wildcard-imports")
    }
}
