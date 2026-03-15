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

buildscript {
    repositories {
        mavenCentral()
    }
}

group = "com.netflix.graphql.dgs"

plugins {
    `java-library`
    id("nebula.dependency-recommender") version "11.0.0"

    id("nebula.netflixoss") version "11.6.0"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.jlleitschuh.gradle.ktlint") version "14.0.1"
    id("me.champeau.jmh") version "0.7.3"

    kotlin("jvm") version Versions.KOTLIN_VERSION
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
    extra["sb.version"] = "4.0.0"
    extra["kotlin.version"] = Versions.KOTLIN_VERSION
}
val internalBomModules by extra(
    listOf(
        project(":graphql-dgs-platform"),
        project(":graphql-dgs-platform-dependencies"),
    ),
)

configure(subprojects.filterNot { it in internalBomModules }) {

    apply {
        plugin("java-library")
        plugin("kotlin")
        plugin("org.jlleitschuh.gradle.ktlint")
        plugin("me.champeau.jmh")
        plugin("io.spring.dependency-management")
    }

    val springBootVersion = extra["sb.version"] as String
    val jmhVersion = "1.37"

    dependencyManagement {
        imports {
            mavenBom("org.jetbrains.kotlin:kotlin-bom:${Versions.KOTLIN_VERSION}")
            mavenBom("org.springframework.boot:spring-boot-dependencies:$springBootVersion")
        }
    }

    dependencies {
        // Apply the BOM to applicable subprojects.
        api(platform(project(":graphql-dgs-platform")))

        // Sets the JMH version to use across modules.
        // Please refer to the following links for further reference.
        // * https://github.com/melix/jmh-gradle-plugin
        // * https://openjdk.java.net/projects/code-tools/jmh/
        jmh("org.openjdk.jmh:jmh-core:$jmhVersion")
        jmh("org.openjdk.jmh:jmh-generator-annprocess:$jmhVersion")

        testImplementation("org.springframework.boot:spring-boot-starter-test") {
            exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
        }

        implementation("org.slf4j:slf4j-api:2.0.17")
        implementation("org.jetbrains:annotations:26.1.0")
        testImplementation("io.mockk:mockk:1.+")

        // JUnit 5 dependencies
        testImplementation(platform("org.junit:junit-bom:5.13.4"))
        testImplementation("org.junit.jupiter:junit-jupiter")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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

    tasks {
        test {
            useJUnitPlatform()
        }
    }

    tasks.withType<Javadoc>().configureEach {
        enabled = false
        options {
            (this as CoreJavadocOptions).addStringOption("Xdoclint:none", "-quiet")
        }
    }

    kotlin {
        jvmToolchain(17)
        compilerOptions {
            javaParameters = true
            freeCompilerArgs.addAll("-Xjvm-default=all-compatibility", "-java-parameters")
        }
    }
}
