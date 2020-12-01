package com.netflix.graphql.dgs;


import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface DgsEnableDataFetcherInstrumentation {
    boolean value() default true;
}
