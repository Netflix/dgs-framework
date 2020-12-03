# dgs-framework

![CI](https://github.com/Netflix/dgs-framework/workflows/CI/badge.svg?branch=master)
[![Apache 2.0](https://img.shields.io/github/license/nebula-plugins/gradle-netflixoss-project-plugin.svg)](http://www.apache.org/licenses/LICENSE-2.0)

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

Create a Spring Boot project (e.g. using the [Initializr](https://start.spring.io)).
Add the following dependency to your build.

Gradle
```groovy
implementation 'com.netflix.graphql.dgs:graphql-dgs:0.0.1-rc.9'
```

Maven
```xml
<dependency>
  <groupId>com.netflix.graphql.dgs</groupId>
  <artifactId>graphql-dgs</artifactId>
  <version>0.0.1-rc.9</version>
  <type>pom</type>
</dependency>
```

Add a schema file under `src/main/resources/schema`.

```graphql
# src/main/resources/schema/schema.graphqls
type Query {
    hello(name: String): String
}
```

Add a data fetcher.

Java
```java
package com.example.demo.datafetchers;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsData;

@DgsComponent
public class HelloDataFetcher {
    @DgsData(parentType = "Query", field = "hello")
    @DgsEnableDataFetcherInstrumentation(false)
    public String hello(@InputArgument("name") String name) {
        if (name == null) {
            name = "Stranger";
        }

        return "hello, " + name + "!";
    }
}
```

Run the application and open http://localhost:8080/graphiql.
Test your query! :shipit:

```graphql
{
 hello(name:"DGS User")
}
```

