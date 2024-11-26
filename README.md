# dgs-framework

![CI](https://github.com/Netflix/dgs-framework/workflows/CI/badge.svg?branch=master)
[![GitHub release](https://img.shields.io/github/v/release/Netflix/dgs-framework.svg)](https://GitHub.com/Netflix/dgs-framework/releases)
[![Apache 2.0](https://img.shields.io/github/license/nebula-plugins/gradle-netflixoss-project-plugin.svg)](http://www.apache.org/licenses/LICENSE-2.0)

Documentation can be found [here](https://netflix.github.io/dgs), including a getting started guide.

The DGS Framework (Domain Graph Service) is a GraphQL server framework for Spring Boot, developed by Netflix.

Features include:

* Annotation based Spring Boot programming model
* Test framework for writing query tests as unit tests
* Gradle Code Generation plugin to create types from schema
* Easy integration with GraphQL Federation
* Integration with Spring Security
* GraphQL subscriptions (WebSockets and SSE)
* File uploads
* Error handling
* Many extension points

# Getting Started

Follow the [getting started guide](https://netflix.github.io/dgs/getting-started/)!

# Upcoming major 10.0 rease
The DGS Framework was deeply integrated with Spring GraphQL earlier this year. 
Details can be found [here](https://netflix.github.io/dgs/spring-graphql-integration).

As announced previously, we will remove the "legacy" code (which doesn't leverage Spring GraphQL) by the end of 2024.
Release 9.2.1 will likely be the final release before this happens.

The next release will be 10.0, where all legacy code is removed.
The existing starter will be updated to switch to the new behavior.
For the majority of applications, this is an invisible change.
At Netflix, we have completed migrating all our applications to the new implementation.
Some issues were found and fixed on the way, but we see no blockers to proceed as planned.

There is a Pull Request that already includes most of the work to delete the legacy code.

*Action: Please manually switch to the new `com.netflix.graphql.dgs:graphql-dgs-spring-graphql-starter` starter to ensure your app runs without problems so that you won't be surprised by the 10.0 version!*


# Contributing, asking questions and reporting issues.

Please read our [contributor guide](CONTRIBUTING.md)!
