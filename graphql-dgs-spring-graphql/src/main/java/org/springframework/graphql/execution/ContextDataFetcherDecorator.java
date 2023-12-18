/*
 * Copyright 2024 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.graphql.execution;

import com.netflix.graphql.dgs.internal.DataFetcherInvoker;
import graphql.GraphQLContext;
import graphql.schema.*;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import io.micrometer.context.ContextSnapshot;
import org.reactivestreams.Publisher;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public final class ContextDataFetcherDecorator implements DataFetcher<Object> {

    private final DataFetcher<?> delegate;

    private final boolean subscription;

    private boolean isDgs = false;

    private final SubscriptionExceptionResolver subscriptionExceptionResolver;

    private ContextDataFetcherDecorator(
            DataFetcher<?> delegate, boolean subscription,
            SubscriptionExceptionResolver subscriptionExceptionResolver) {

        Assert.notNull(delegate, "'delegate' DataFetcher is required");
        Assert.notNull(subscriptionExceptionResolver, "'subscriptionExceptionResolver' is required");
        this.delegate = delegate;
        this.subscription = subscription;
        this.subscriptionExceptionResolver = subscriptionExceptionResolver;

        if(delegate instanceof DataFetcherInvoker) {
            isDgs = true;
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public Object get(DataFetchingEnvironment environment) throws Exception {

        if(isDgs) {
            return this.delegate.get(environment);
        }

        GraphQLContext context;
        // temporarily merge global and local graphql context until https://github.com/micrometer-metrics/context-propagation/pull/98
        if (environment.getLocalContext() instanceof GraphQLContext localContext) {
            context = GraphQLContext.newContext()
                    .of(environment.getGraphQlContext())
                    .of(localContext)
                    .build();
        }
        else {
            context = environment.getGraphQlContext();
        }
        ContextSnapshot snapshot = ContextSnapshot.captureFrom(context);
        Object value = snapshot.wrap(() -> this.delegate.get(environment)).call();

        if (this.subscription) {
            Assert.state(value instanceof Publisher, "Expected Publisher for a subscription");
            Flux<?> flux = Flux.from((Publisher<?>) value).onErrorResume(exception -> {
                // Already handled, e.g. controller methods?
                if (exception instanceof SubscriptionPublisherException) {
                    return Mono.error(exception);
                }
                return this.subscriptionExceptionResolver.resolveException(exception)
                        .flatMap(errors -> Mono.error(new SubscriptionPublisherException(errors, exception)));
            });
            return flux.contextWrite(snapshot::updateContext);
        }

        if (value instanceof Flux) {
            value = ((Flux<?>) value).collectList();
        }

        if (value instanceof Mono<?> valueMono) {
            value = valueMono.contextWrite(snapshot::updateContext).toFuture();
        }

        return value;
    }


    /**
     * Static factory method to create {@link GraphQLTypeVisitor} that wraps
     * data fetchers with the {@link ContextDataFetcherDecorator}.
     */
    public static GraphQLTypeVisitor createVisitor(List<SubscriptionExceptionResolver> resolvers) {
        return new ContextTypeVisitor(resolvers);
    }


    /**
     * Type visitor to apply {@link ContextDataFetcherDecorator}.
     */
    private static class ContextTypeVisitor extends GraphQLTypeVisitorStub {

        private final SubscriptionExceptionResolver exceptionResolver;

        private ContextTypeVisitor(List<SubscriptionExceptionResolver> resolvers) {
            this.exceptionResolver = new CompositeSubscriptionExceptionResolver(resolvers);
        }

        @Override
        public TraversalControl visitGraphQLFieldDefinition(
                GraphQLFieldDefinition fieldDefinition, TraverserContext<GraphQLSchemaElement> context) {

            TypeVisitorHelper visitorHelper = context.getVarFromParents(TypeVisitorHelper.class);
            GraphQLCodeRegistry.Builder codeRegistry = context.getVarFromParents(GraphQLCodeRegistry.Builder.class);

            GraphQLFieldsContainer parent = (GraphQLFieldsContainer) context.getParentNode();
            DataFetcher<?> dataFetcher = codeRegistry.getDataFetcher(parent, fieldDefinition);

            if (applyDecorator(dataFetcher)) {
                boolean handlesSubscription = visitorHelper.isSubscriptionType(parent);
                dataFetcher = new ContextDataFetcherDecorator(dataFetcher, handlesSubscription, exceptionResolver);
                codeRegistry.dataFetcher(parent, fieldDefinition, dataFetcher);
            }

            return TraversalControl.CONTINUE;
        }

        private boolean applyDecorator(DataFetcher<?> dataFetcher) {
            Class<?> type = dataFetcher.getClass();
            String packageName = type.getPackage().getName();
            if (packageName.startsWith("graphql.")) {
                return (type.getSimpleName().startsWith("DataFetcherFactories") ||
                        packageName.startsWith("graphql.validation"));
            }
            return true;
        }
    }

}