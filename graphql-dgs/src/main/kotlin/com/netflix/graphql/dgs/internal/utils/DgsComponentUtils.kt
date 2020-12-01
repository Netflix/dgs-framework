package com.netflix.graphql.dgs.internal.utils

class DgsComponentUtils {
    companion object {
        fun getClassEnhancedBySpringCGLib(dgsComponent: Any): Class<*> {
            return when {
                dgsComponent.javaClass.name.contains("EnhancerBySpringCGLIB") -> dgsComponent.javaClass.superclass
                else -> dgsComponent.javaClass
            }
        }
    }
}