package com.netflix.graphql.dgs.exceptions

import java.lang.RuntimeException

class NoSchemaFoundException: RuntimeException("No schema files found. Define schemas in src/main/resources/schema/*.graphqls")