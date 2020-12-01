plugins {
    kotlin("jvm")
}

dependencies {
    api("com.graphql-java:graphql-java:${Versions.GRAPHQL_JAVA}")
    implementation("com.github.javafaker:javafaker:1.+")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.+")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.+")
    testImplementation("org.assertj:assertj-core:3.+")
}


