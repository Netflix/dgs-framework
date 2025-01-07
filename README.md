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

# DGS 10.x has been released! 🎉

DGS 10.0.0 removes all the legacy code in favor of our integration with Spring for GraphQL.
In March 2024 we released deep integration with Spring for GraphQL after working closely with the Spring team.
This integration makes it possible to mix and match features from DGS and Spring for GraphQL, and leverages the web transports provided by Spring for GraphQL.
With the March released we declared the "old" DGS starter, and the implementation code legacy, with the plan to remove this code end of 2024.
The community has adopted the DGS/Spring for GraphQL integration really well, in most cases without any required code changes.
At Netflix we migrated all our services to use the new integration, again mostly without any code changes.
Performance is critical for our services, and after all the performance optimization that went into the March release and some patch releases after, we see the same performance with the Spring for GraphQL integration as what we had previously.

DGS 10.0.0 finalizes the integration work by removing all the legacy modules and code.
This greatly reduces the footprint of the codebase, which will speed up feature development into the future!

Although the list of changes is large, you probably won't notice the difference for your applications!
Just make sure to use the (new) `com.netflix.graphql.dgs:dgs-starter` AKA `com.netflix.graphql.dgs:graphql-dgs-spring-graphql-starter` starter!

See [release notes](https://github.com/Netflix/dgs-framework/releases/tag/v10.0.0) for a detailed overview of changes.

# Contributing, asking questions and reporting issues.

Please read our [contributor guide](CONTRIBUTING.md)!
