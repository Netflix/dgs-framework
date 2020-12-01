package com.netflix.graphql.dgs;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark a method a provider of a CodeRegistry, which is a programmatic way of creating a schema.
 * http://manuals.test.netflix.net/view/dgs/mkdocs/master/type-and-code-registry/
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DgsCodeRegistry {
}
