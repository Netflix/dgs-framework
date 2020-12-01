package com.netflix.graphql.dgs.exceptions

class MultipleDataLoadersDefinedException(clazz: Class<*>): RuntimeException("Multiple data loaders found, unable to disambiguate for ${clazz.name}.")