package com.netflix.graphql.dgs;

import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class or field as a DataLoader, which will be registered to the framework as a DataLoader.
 * The class or field must implement one of the BatchLoader interfaces.
 * See http://manuals.test.netflix.net/view/dgs/mkdocs/master/dataloaders/
 */
@Target({ElementType.TYPE, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface DgsDataLoader {
    String name();
    boolean caching() default true;
    boolean batching() default true;
    int maxBatchSize() default 0;
}

