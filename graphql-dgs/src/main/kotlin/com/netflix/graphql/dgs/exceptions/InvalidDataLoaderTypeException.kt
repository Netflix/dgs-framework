package com.netflix.graphql.dgs.exceptions

class InvalidDataLoaderTypeException(clazz: Class<*>): RuntimeException("@DgsDataLoader found that doesn't implement BatchLoader: ${clazz.name}.")