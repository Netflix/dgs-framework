package com.netflix.graphql.dgs.exceptions

import graphql.GraphQLError
import java.lang.RuntimeException

class QueryException(val errors: List<GraphQLError>): RuntimeException(errors.joinToString(separator = ", ") { it.message })