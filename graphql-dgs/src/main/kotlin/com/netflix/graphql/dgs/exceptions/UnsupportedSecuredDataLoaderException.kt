package com.netflix.graphql.dgs.exceptions

class UnsupportedSecuredDataLoaderException(clazz: Class<*>): RuntimeException("Field level @DgsDataLoader is not supported on classes that use @Secured. Move your @DgsDataLoader to its own class. The offending field is in: ${clazz.name}")