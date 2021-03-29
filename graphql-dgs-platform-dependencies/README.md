# GraphQL DGS Bill of Materials (BOM) with Dependencies

This module provides a Bill of Materials (BOM) with to ease dependency management using [Maven] or [Gradle].
This specific BOM will not only contain references to DGS Framework Modules but includes recommendations 
used internally. In other words, it will include the `dependencyRecomendations` [here](https://github.com/Netflix/dgs-framework/blob/master/build.gradle.kts#L57).

[Maven]:      https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#Importing_Dependencies
[Gradle]:     https://docs.gradle.org/current/userguide/managing_transitive_dependencies.html#sec:bom_import