/*
 * Copyright 2020 Netflix, Inc.
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
        jcenter()
    }
}

group = "com.netflix.graphql.dgs"

plugins {
    `java-library`
    id("nebula.netflixoss") version "8.8.1"
    id("nebula.dependency-recommender") version "9.1.1"
    kotlin("jvm") version Versions.KOTLIN_VERSON apply false
    idea
    eclipse
}

dependencyRecommendations {
    mavenBom(module("org.springframework:spring-framework-bom:${Versions.SPRING_VERSION}"))
//    mavenBom(module("org.springframework.boot:spring-boot-dependencies:${Versions.SPRING_BOOT_VERSION}"))
//    mavenBom(module("org.springframework.security:spring-security-bom:${Versions.SPRING_SECURITY_VERSION}"))
//    mavenBom(module("org.springframework.cloud:spring-cloud-dependencies:${Versions.SPRING_CLOUD_VERSION}"))
}


allprojects {
    group = "com.netflix.graphql.dgs"
    repositories {
        jcenter()
    }

    apply(plugin = "java-library")
    apply(plugin = "nebula.netflixoss")


    dependencies {
        testImplementation("org.springframework.boot:spring-boot-starter-test") {
            exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
        }
    }
    java {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = "1.8"
        }
    }


    tasks.withType<Test> {
        useJUnitPlatform()
    }

    configurations.all {
        resolutionStrategy {
            force("org.jetbrains.kotlin:kotlin-reflect:${Versions.KOTLIN_VERSON}")
        }
    }
}
