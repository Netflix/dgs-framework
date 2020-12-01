package com.netflix.graphql.dgs;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark a function as a provider of runtime wiring.
 * This allows customization of the runtime wiring before it is made executable.
 * This is a low level extension feature that is typically not necessary.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DgsRuntimeWiring {
}
