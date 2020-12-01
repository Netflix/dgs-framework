package com.netflix.graphql.dgs.exceptions

import java.lang.Exception

class DgsInvalidInputArgumentException(override val message: String, override val cause: Exception? = null):RuntimeException(message, cause)