# GraphQL DGS Bill of Materials (BOM)

This module provides a Bill of Materials (BOM) to ease dependency management using [Maven] or [Gradle].
This specific BOM will only contain references to DGS Framework Modules and other dependencies that such modules
expose as part of their _API_. If you are looking for a BOM that will not only provide these recommendations
but additional recommendations that the DGS Framework uses internally, you need the `graphql-dgs-platform-dependencies` BOM.

[Maven]:      https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#Importing_Dependencies
[Gradle]:     https://docs.gradle.org/current/userguide/managing_transitive_dependencies.html#sec:bom_import