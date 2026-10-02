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

package com.netflix.graphql.dgs;

import com.netflix.graphql.dgs.context.DgsContext;
import com.netflix.graphql.dgs.exceptions.MultipleDataLoadersDefinedException;
import com.netflix.graphql.dgs.exceptions.NoDataLoaderFoundException;
import com.netflix.graphql.dgs.internal.utils.DataLoaderNameUtil;
import graphql.GraphQLContext;
import graphql.execution.ExecutionId;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.MergedField;
import graphql.execution.directives.QueryDirectives;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.OperationDefinition;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingFieldSelectionSet;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.type.StandardMethodMetadata;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DgsDataFetchingEnvironment implements DataFetchingEnvironment {
    private final DataFetchingEnvironment dfe;
    private final ApplicationContext ctx;

    public DgsDataFetchingEnvironment(@NotNull DataFetchingEnvironment dfe, @NotNull ApplicationContext ctx) {
        this.dfe = dfe;
        this.ctx = ctx;
    }

    @NotNull
    public DataFetchingEnvironment getDfe() {
        return this.dfe;
    }

    @NotNull
    public DgsContext getDgsContext() {
        return DgsContext.from(this);
    }

    /**
     * Get the value of the current object to be queried.
     *
     * @throws IllegalStateException if called on the root query
     */
    public <T> T getSourceOrThrow() {
        T source = getSource();
        if (source == null) {
            throw new IllegalStateException("source is null");
        }
        return source;
    }

    @NotNull
    public <K, V> DataLoader<K, V> getDataLoader(@NotNull Class<?> loaderClass) {
        DgsDataLoader annotation = loaderClass.getAnnotation(DgsDataLoader.class);
        String loaderName;
        if (annotation != null) {
            loaderName = DataLoaderNameUtil.getDataLoaderName(loaderClass, annotation);
        } else {
            List<java.lang.reflect.Field> loaders = Arrays.stream(loaderClass.getFields())
                    .filter(field -> field.isAnnotationPresent(DgsDataLoader.class))
                    .toList();
            if (loaders.isEmpty()) {
                // annotation is not on the class, but potentially on the Bean definition
                loaderName = tryGetDataLoaderFromBeanDefinition(loaderClass);
            } else {
                if (loaders.size() > 1) {
                    throw new MultipleDataLoadersDefinedException(loaderClass);
                }
                loaderName = loaders.get(0).getAnnotation(DgsDataLoader.class).name();
            }
        }

        DataLoader<K, V> dataLoader = getDataLoader(loaderName);
        if (dataLoader == null) {
            throw new NoDataLoaderFoundException("DataLoader with name " + loaderName + " not found");
        }
        return dataLoader;
    }

    private String tryGetDataLoaderFromBeanDefinition(Class<?> loaderClass) {
        String name = loaderClass.getSimpleName();
        if (ctx instanceof ConfigurableApplicationContext configurableContext) {
            Map<String, ?> beansOfType = configurableContext.getBeanFactory().getBeansOfType(loaderClass);
            if (beansOfType.isEmpty()) {
                throw new NoDataLoaderFoundException(loaderClass);
            }
            if (beansOfType.size() > 1) {
                throw new MultipleDataLoadersDefinedException(loaderClass);
            }
            String beanName = beansOfType.keySet().iterator().next();
            BeanDefinition beanDefinition = configurableContext.getBeanFactory().getBeanDefinition(beanName);
            if (beanDefinition.getSource() instanceof StandardMethodMetadata methodMetadata) {
                Method method = methodMetadata.getIntrospectedMethod();
                DgsDataLoader methodAnnotation = method.getAnnotation(DgsDataLoader.class);
                name = DataLoaderNameUtil.getDataLoaderName(loaderClass, methodAnnotation);
            }
        }
        return name;
    }

    /**
     * Check if an argument is explicitly set using "argument.nested.property" or "argument-&gt;nested-&gt;property"
     * syntax. Note that this requires String splitting which is expensive for hot code paths.
     * Use {@link #isArgumentSet(String...)} as a faster alternative.
     */
    public boolean isNestedArgumentSet(@NotNull String path) {
        String[] pathParts = Arrays.stream(path.split("\\.|->")).map(String::trim).toArray(String[]::new);
        return isArgumentSet(pathParts);
    }

    /**
     * Check if an argument is explicitly set.
     * For complex object arguments, use the isArgumentSet("root", "nested", "property") syntax.
     */
    @SuppressWarnings("unchecked")
    public boolean isArgumentSet(@NotNull String... path) {
        Map<String, Object> args = dfe.getExecutionStepInfo().getArguments();
        for (String key : path) {
            // Explicitly check contains to support explicit null values
            if (!args.containsKey(key)) {
                return false;
            }
            Object value = args.get(key);
            if (!(value instanceof Map<?, ?>)) {
                return true;
            }
            args = (Map<String, Object>) value;
        }
        return true;
    }

    @Nullable
    @Override
    public <T> T getSource() {
        return dfe.getSource();
    }

    @NotNull
    @Override
    public Map<String, Object> getArguments() {
        return dfe.getArguments();
    }

    @Override
    public boolean containsArgument(@NotNull String name) {
        return dfe.containsArgument(name);
    }

    @Nullable
    @Override
    public <T> T getArgument(@NotNull String name) {
        return dfe.getArgument(name);
    }

    @NotNull
    @Override
    public <T> T getArgumentOrDefault(@NotNull String name, @NotNull T defaultValue) {
        return dfe.getArgumentOrDefault(name, defaultValue);
    }

    @Nullable
    @Override
    @SuppressWarnings("deprecation")
    public <T> T getContext() {
        return dfe.getContext();
    }

    @NotNull
    @Override
    public GraphQLContext getGraphQlContext() {
        return dfe.getGraphQlContext();
    }

    @Nullable
    @Override
    public <T> T getLocalContext() {
        return dfe.getLocalContext();
    }

    @Nullable
    @Override
    public <T> T getRoot() {
        return dfe.getRoot();
    }

    @NotNull
    @Override
    public GraphQLFieldDefinition getFieldDefinition() {
        return dfe.getFieldDefinition();
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public List<Field> getFields() {
        return dfe.getFields();
    }

    @NotNull
    @Override
    public MergedField getMergedField() {
        return dfe.getMergedField();
    }

    @NotNull
    @Override
    public Field getField() {
        return dfe.getField();
    }

    @NotNull
    @Override
    public GraphQLOutputType getFieldType() {
        return dfe.getFieldType();
    }

    @NotNull
    @Override
    public ExecutionStepInfo getExecutionStepInfo() {
        return dfe.getExecutionStepInfo();
    }

    @NotNull
    @Override
    public GraphQLType getParentType() {
        return dfe.getParentType();
    }

    @NotNull
    @Override
    public GraphQLSchema getGraphQLSchema() {
        return dfe.getGraphQLSchema();
    }

    @NotNull
    @Override
    public Map<String, FragmentDefinition> getFragmentsByName() {
        return dfe.getFragmentsByName();
    }

    @NotNull
    @Override
    public ExecutionId getExecutionId() {
        return dfe.getExecutionId();
    }

    @NotNull
    @Override
    public DataFetchingFieldSelectionSet getSelectionSet() {
        return dfe.getSelectionSet();
    }

    @NotNull
    @Override
    public QueryDirectives getQueryDirectives() {
        return dfe.getQueryDirectives();
    }

    @Nullable
    @Override
    public <K, V> DataLoader<K, V> getDataLoader(@NotNull String dataLoaderName) {
        return dfe.getDataLoader(dataLoaderName);
    }

    @NotNull
    @Override
    public DataLoaderRegistry getDataLoaderRegistry() {
        return dfe.getDataLoaderRegistry();
    }

    @NotNull
    @Override
    public Locale getLocale() {
        return dfe.getLocale();
    }

    @NotNull
    @Override
    public OperationDefinition getOperationDefinition() {
        return dfe.getOperationDefinition();
    }

    @NotNull
    @Override
    public Document getDocument() {
        return dfe.getDocument();
    }

    @NotNull
    @Override
    public Map<String, Object> getVariables() {
        return dfe.getVariables();
    }

    @NotNull
    @Override
    public Object toInternal() {
        return dfe.toInternal();
    }
}
