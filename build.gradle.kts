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
import java.net.URI

buildscript {
    repositories {
        mavenCentral()
    }
}

group = "com.netflix.graphql.dgs"

plugins {
    `java-library`
    id("nebula.dependency-recommender") version "11.0.0"

    id("nebula.netflixoss") version "11.4.0"
    id("io.spring.dependency-management") version "1.1.6"

    id("org.jmailen.kotlinter") version "4.4.+"
    id("me.champeau.jmh") version "0.7.2"

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
    // in buildSrc, most likely because the variables are used in plugins as well
    // as dependencies. e.g. KOTLIN_VERSION
    extra["sb.version"] = "3.3.2"
    extra["kotlin.version"] = Versions.KOTLIN_VERSION
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
        plugin("org.jmailen.kotlinter")
        plugin("me.champeau.jmh")
        plugin("io.spring.dependency-management")
    }

    val springBootVersion = extra["sb.version"] as String
    val jmhVersion = "1.37"

    dependencyManagement {
        imports {
            mavenBom("org.jetbrains.kotlin:kotlin-bom:${Versions.KOTLIN_VERSION}")
            mavenBom("org.springframework.boot:spring-boot-dependencies:${springBootVersion}")
        }
    }

    dependencies {
        // Apply the BOM to applicable subprojects.
        api(platform(project(":graphql-dgs-platform")))
        // Speed up processing of AutoConfig's produced by Spring Boot
        annotationProcessor("org.springframework.boot:spring-boot-autoconfigure-processor")
        // Produce Config Metadata for properties used in Spring Boot
        annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

        // Sets the JMH version to use across modules.
        // Please refer to the following links for further reference.
        // * https://github.com/melix/jmh-gradle-plugin
        // * https://openjdk.java.net/projects/code-tools/jmh/
        jmh("org.openjdk.jmh:jmh-core:${jmhVersion}")
        jmh("org.openjdk.jmh:jmh-generator-annprocess:${jmhVersion}")

        testImplementation("org.springframework.boot:spring-boot-starter-test") {
            exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
        }
        testImplementation("io.mockk:mockk:1.+")
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }

    jmh {
        includeTests.set(true)
        jmhTimeout.set("5s")
        timeUnit.set("ms")
        warmupIterations.set(2)
        iterations.set(2)
        fork.set(2)
        duplicateClassesStrategy.set(DuplicatesStrategy.EXCLUDE)
    }

    tasks.withType<Jar> {
        duplicatesStrategy = DuplicatesStrategy.WARN
    }

    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs.addAll(listOf("-parameters", "-deprecation"))
    }

    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions {
            /*
             * Prior to Kotlin 1.6 we had `jvm-default=enable`, 1.6.20 adds `-Xjvm-default=all-compatibility`
             *   > .. generate compatibility stubs in the DefaultImpls classes.
             *   > Compatibility stubs could be useful for library and runtime authors to keep backward binary
             *   > compatibility for existing clients compiled against previous library versions.
             * Ref. https://kotlinlang.org/docs/kotlin-reference.pdf
             */
            freeCompilerArgs = freeCompilerArgs + "-Xjvm-default=all-compatibility" + "-java-parameters"
            jvmTarget = "17"
        }
    }

    tasks {
        test {
            useJUnitPlatform()
        }
    }

    tasks.withType<Javadoc>().configureEach {
        options {
            (this as CoreJavadocOptions).addStringOption("Xdoclint:none", "-quiet")
        }
    }

    kotlinter {
        reporters = arrayOf("checkstyle", "plain")
    }
}
