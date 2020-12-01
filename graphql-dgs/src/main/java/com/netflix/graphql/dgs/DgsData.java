package com.netflix.graphql.dgs;

import java.lang.annotation.*;

/**
 * Mark a method to be a data fetcher.
 * A data fetcher can receive the DataFetchingEnvironment.
 * The "parentType" property is the type that contains this field.
 * For root queries that is "Query", and for root mutations "Mutation".
 * The field is the name of the field this data fetcher is responsible for.
 * See http://manuals.test.netflix.net/view/dgs/mkdocs/master/getting-started/
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface DgsData {
    String parentType();
    String field();
}
