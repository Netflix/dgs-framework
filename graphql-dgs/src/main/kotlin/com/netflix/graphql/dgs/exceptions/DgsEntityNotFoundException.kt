package com.netflix.graphql.dgs.exceptions

class DgsEntityNotFoundException(override val message: String = "Requested entity not found"): RuntimeException(message)