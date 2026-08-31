/*
 * Copyright 2025 Netflix, Inc.
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

package com.netflix.graphql.dgs.federation;

import com.apollographql.federation.graphqljava._Entity;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import com.netflix.graphql.dgs.DgsFederationResolver;
import com.netflix.graphql.dgs.exceptions.InvalidDgsEntityFetcher;
import com.netflix.graphql.dgs.exceptions.MissingDgsEntityFetcherException;
import com.netflix.graphql.dgs.exceptions.MissingFederatedQueryArgument;
import com.netflix.graphql.dgs.internal.EntityFetcherRegistry;
import com.netflix.graphql.types.errors.TypedGraphQLError;
import graphql.GraphQLContext;
import graphql.GraphQLError;
import graphql.execution.DataFetcherExceptionHandler;
import graphql.execution.DataFetcherExceptionHandlerParameters;
import graphql.execution.DataFetcherResult;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.ResultPath;
import graphql.schema.Coercing;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
import graphql.schema.GraphQLObjectType;
import graphql.schema.TypeResolver;
import kotlin.Pair;
import org.dataloader.Try;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.util.ReflectionUtils;
import reactor.core.publisher.Mono;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

@DgsComponent
public class DefaultDgsFederationResolver implements DgsFederationResolver {
    private static final Logger logger = LoggerFactory.getLogger(DefaultDgsFederationResolver.class);

    /** Used when the DefaultDgsFederationResolver is extended. */
    @Autowired
    private EntityFetcherRegistry entityFetcherRegistry;

    @Autowired
    private Optional<DataFetcherExceptionHandler> dgsExceptionHandler;

    @Autowired
    private ApplicationContext applicationContext;

    private final DataFetcher<Object> entitiesDataFetcher = this::dgsEntityFetchers;

    /**
     * The default constructor is used to extend the DefaultDgsFederationResolver. In that case injection is used to
     * provide the schemaProvider.
     */
    public DefaultDgsFederationResolver() {
    }

    /**
     * This constructor is used by DgsSchemaProvider when no custom DgsFederationResolver is provided.
     * This is the most common use case.
     */
    public DefaultDgsFederationResolver(
            EntityFetcherRegistry entityFetcherRegistry,
            Optional<DataFetcherExceptionHandler> dataFetcherExceptionHandler,
            ApplicationContext applicationContext) {
        this.entityFetcherRegistry = entityFetcherRegistry;
        this.dgsExceptionHandler = dataFetcherExceptionHandler;
        this.applicationContext = applicationContext;
    }

    public EntityFetcherRegistry getEntityFetcherRegistry() {
        return entityFetcherRegistry;
    }

    public void setEntityFetcherRegistry(EntityFetcherRegistry entityFetcherRegistry) {
        this.entityFetcherRegistry = entityFetcherRegistry;
    }

    public Optional<DataFetcherExceptionHandler> getDgsExceptionHandler() {
        return dgsExceptionHandler;
    }

    public void setDgsExceptionHandler(Optional<DataFetcherExceptionHandler> dgsExceptionHandler) {
        this.dgsExceptionHandler = dgsExceptionHandler;
    }

    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public DataFetcher<Object> entitiesFetcher() {
        return entitiesDataFetcher;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> valuesWithMappedScalars(
            GraphQLContext graphQLContext,
            Map<String, Object> values,
            Map<List<String>, Coercing<?, ?>> scalarMappings,
            List<String> currentPath) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            currentPath.add(entry.getKey());

            Coercing<?, ?> converter = scalarMappings.get(currentPath);
            Object value = entry.getValue();
            Object newValue;
            if (converter != null) {
                newValue = converter.parseValue(value, graphQLContext, Locale.getDefault());
            } else if (value instanceof Map<?, ?> mapValue) {
                newValue = valuesWithMappedScalars(
                        graphQLContext, (Map<String, Object>) mapValue, scalarMappings, currentPath);
            } else {
                newValue = value;
            }

            currentPath.remove(currentPath.size() - 1);
            result.put(entry.getKey(), newValue);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private CompletableFuture<DataFetcherResult<List<Object>>> dgsEntityFetchers(DataFetchingEnvironment env) {
        List<Map<String, Object>> argument = env.getArgument(_Entity.argumentName);
        List<Map<String, Object>> arguments = argument != null ? argument : List.of();

        List<CompletableFuture<Try<Object>>> resultList = new ArrayList<>(arguments.size());
        for (Map<String, Object> values : arguments) {
            resultList.add(fetchEntity(env, values));
        }

        return CompletableFuture.allOf(resultList.toArray(new CompletableFuture[0])).thenApply(ignored -> {
            List<Try<Object>> tryResults = resultList.stream().map(CompletableFuture::join).toList();

            List<Object> data = new ArrayList<>();
            for (Try<Object> tryResult : tryResults) {
                Object value = tryResult.orElse(null);
                if (value instanceof Collection<?> collection) {
                    data.addAll(collection);
                } else {
                    data.add(value);
                }
            }

            List<GraphQLError> errors = new ArrayList<>();
            for (int idx = 0; idx < tryResults.size(); idx++) {
                Try<Object> tryResult = tryResults.get(idx);
                if (!tryResult.isFailure()) {
                    continue;
                }
                errors.addAll(toErrors(env, idx, tryResult.getThrowable()));
            }

            return DataFetcherResult.<List<Object>>newResult().data(data).errors(errors).build();
        });
    }

    @SuppressWarnings("unchecked")
    private CompletableFuture<Try<Object>> fetchEntity(DataFetchingEnvironment env, Map<String, Object> values) {
        try {
            Object typename = values.get("__typename");
            if (typename == null) {
                throw new MissingFederatedQueryArgument("__typename");
            }
            Pair<Object, Method> entityFetcher = entityFetcherRegistry.getEntityFetchers().get(typename);
            if (entityFetcher == null) {
                throw new MissingDgsEntityFetcherException(typename.toString());
            }
            Object target = entityFetcher.getFirst();
            Method method = entityFetcher.getSecond();

            Class<?>[] parameterTypes = method.getParameterTypes();
            boolean acceptsMap = false;
            for (Class<?> parameterType : parameterTypes) {
                if (parameterType.isAssignableFrom(Map.class)) {
                    acceptsMap = true;
                    break;
                }
            }
            if (!acceptsMap) {
                throw new InvalidDgsEntityFetcher("@DgsEntityFetcher " + target.getClass().getName() + "."
                        + method.getName() + " is invalid. A DgsEntityFetcher must accept an argument of type "
                        + "Map<String, Object>");
            }

            Map<List<String>, Coercing<?, ?>> inputMappings =
                    entityFetcherRegistry.getEntityFetcherInputMappings().get(typename);
            Map<String, Object> coercedValues = inputMappings != null
                    ? valuesWithMappedScalars(env.getGraphQlContext(), values, inputMappings, new ArrayList<>())
                    : values;

            List<Object> objects = new ArrayList<>();
            if (parameterTypes.length == 1) {
                objects.add(coercedValues);
            } else if (parameterTypes.length == 2) {
                for (Class<?> type : parameterTypes) {
                    if (type.isAssignableFrom(Map.class)) {
                        objects.add(coercedValues);
                    } else if (type.isAssignableFrom(DgsDataFetchingEnvironment.class)) {
                        objects.add(new DgsDataFetchingEnvironment(env, applicationContext));
                    } else {
                        throw new InvalidDgsEntityFetcher("@DgsEntityFetcher " + target.getClass().getName() + "."
                                + method.getName() + " is invalid. A DgsEntityFetcher can only accept arguments of "
                                + "type Map<String, Object> or DgsDataFetchingEnvironment");
                    }
                }
            } else {
                throw new InvalidDgsEntityFetcher("@DgsEntityFetcher " + target.getClass().getName() + "."
                        + method.getName() + " is invalid. A DgsEntityFetcher can only accept up to 2 arguments");
            }
            Object result = ReflectionUtils.invokeMethod(method, target, objects.toArray());

            if (result == null) {
                logger.error("@DgsEntityFetcher returned null for type: {}", typename);
            }

            CompletableFuture<Object> future;
            if (result instanceof CompletionStage<?> completionStage) {
                future = (CompletableFuture<Object>) completionStage.toCompletableFuture();
            } else if (result instanceof Mono<?> mono) {
                future = (CompletableFuture<Object>) mono.toFuture();
            } else {
                future = CompletableFuture.completedFuture(result);
            }
            return Try.tryFuture(future);
        } catch (Throwable exception) {
            return CompletableFuture.completedFuture(Try.failed(exception));
        }
    }

    private List<GraphQLError> toErrors(DataFetchingEnvironment env, int idx, Throwable throwable) {
        // extract exception from known wrapper types
        Throwable exception;
        if (throwable instanceof InvocationTargetException invocationTargetException) {
            exception = invocationTargetException.getTargetException();
        } else if (throwable instanceof CompletionException && throwable.getCause() != null) {
            exception = throwable.getCause();
        } else {
            exception = throwable;
        }

        // handle the exception (using the custom handler if present)
        if (dgsExceptionHandler != null && dgsExceptionHandler.isPresent()) {
            DgsDataFetchingEnvironment dfeWithErrorPath = createDataFetchingEnvironmentWithPath(env, idx);
            return dgsExceptionHandler
                    .get()
                    .handleException(DataFetcherExceptionHandlerParameters
                            .newExceptionParameters()
                            .dataFetchingEnvironment(dfeWithErrorPath)
                            .exception(exception)
                            .build())
                    .join()
                    .getErrors();
        }
        return List.of(TypedGraphQLError
                .newInternalErrorBuilder()
                .message(exception.getClass().getName() + ": " + exception.getMessage())
                .path(ResultPath.fromList(List.of("/_entities", idx)))
                .build());
    }

    public DgsDataFetchingEnvironment createDataFetchingEnvironmentWithPath(
            DataFetchingEnvironment env, int pathIndex) {
        ResultPath pathWithIndex = env.getExecutionStepInfo().getPath().segment(pathIndex);
        ExecutionStepInfo executionStepInfoWithPath = ExecutionStepInfo
                .newExecutionStepInfo(env.getExecutionStepInfo())
                .path(pathWithIndex)
                .build();
        DataFetchingEnvironment dfe =
                env instanceof DgsDataFetchingEnvironment dgsEnv ? dgsEnv.getDfe() : env;
        return new DgsDataFetchingEnvironment(
                DataFetchingEnvironmentImpl.newDataFetchingEnvironment(dfe)
                        .executionStepInfo(executionStepInfoWithPath)
                        .build(),
                applicationContext);
    }

    public Map<Class<?>, String> typeMapping() {
        return Map.of();
    }

    @Override
    public TypeResolver typeResolver() {
        return env -> {
            Object src = env.getObject();

            String typeName = typeMapping().containsKey(src.getClass())
                    ? typeMapping().get(src.getClass())
                    : src.getClass().getSimpleName();

            GraphQLObjectType type = env.getSchema().getObjectType(typeName);
            if (type == null) {
                logger.warn(
                        "No type definition found for {}. You probably need to provide either a type mapping,"
                                + "or override DefaultDgsFederationResolver.typeResolver()."
                                + "Alternatively make sure the type name in the schema and your Java model match",
                        src.getClass().getName());
            }

            return type;
        };
    }
}
