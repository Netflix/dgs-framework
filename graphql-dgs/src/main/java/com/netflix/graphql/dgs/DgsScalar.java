package com.netflix.graphql.dgs;

import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Mark a class as a custom Scalar implementation that gets registered to the framework.
 * See http://manuals.test.netflix.net/view/dgs/mkdocs/master//scalars/
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface DgsScalar {
    String name();
}
