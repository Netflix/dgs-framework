package com.netflix.graphql.dgs.exceptions

class NoDataLoaderFoundException(clazz: Class<*>): RuntimeException("No data loader found. Missing @DgsDataLoader for ${clazz.name}.")