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

package com.netflix.graphql.dgs.internal;

import com.netflix.graphql.dgs.DataLoaderInstrumentationExtensionProvider;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsDataLoader;
import com.netflix.graphql.dgs.DgsDataLoaderCustomizer;
import com.netflix.graphql.dgs.DgsDataLoaderOptionsProvider;
import com.netflix.graphql.dgs.DgsDataLoaderRegistryConsumer;
import com.netflix.graphql.dgs.DgsDispatchPredicate;
import com.netflix.graphql.dgs.exceptions.DgsUnnamedDataLoaderOnFieldException;
import com.netflix.graphql.dgs.exceptions.InvalidDataLoaderTypeException;
import com.netflix.graphql.dgs.exceptions.MultipleDataLoadersDefinedException;
import com.netflix.graphql.dgs.exceptions.UnsupportedSecuredDataLoaderException;
import com.netflix.graphql.dgs.internal.utils.DataLoaderNameUtil;
import jakarta.annotation.PostConstruct;
import org.dataloader.BatchLoader;
import org.dataloader.BatchLoaderWithContext;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderFactory;
import org.dataloader.DataLoaderOptions;
import org.dataloader.DataLoaderRegistry;
import org.dataloader.MappedBatchLoader;
import org.dataloader.MappedBatchLoaderWithContext;
import org.dataloader.registries.DispatchPredicate;
import org.dataloader.registries.ScheduledDataLoaderRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.type.StandardMethodMetadata;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

/** Framework implementation class responsible for finding and configuring data loaders. */
public class DefaultDgsDataLoaderProvider implements DgsDataLoaderProvider {
    private static final Logger logger = LoggerFactory.getLogger(DefaultDgsDataLoaderProvider.class);

    private final ApplicationContext applicationContext;
    private final List<DataLoaderInstrumentationExtensionProvider> extensionProviders;
    private final List<DgsDataLoaderCustomizer> customizers;
    private final DgsDataLoaderOptionsProvider dataLoaderOptionsProvider;
    private final ScheduledExecutorService scheduledExecutorService;
    private final Duration scheduleDuration;
    private final boolean enableTickerMode;

    private final Map<String, Class<?>> dataLoaders = new HashMap<>();
    private final List<LoaderHolder<BatchLoader<?, ?>>> batchLoaders = new ArrayList<>();
    private final List<LoaderHolder<BatchLoaderWithContext<?, ?>>> batchLoadersWithContext = new ArrayList<>();
    private final List<LoaderHolder<MappedBatchLoader<?, ?>>> mappedBatchLoaders = new ArrayList<>();
    private final List<LoaderHolder<MappedBatchLoaderWithContext<?, ?>>> mappedBatchLoadersWithContext =
            new ArrayList<>();

    public DefaultDgsDataLoaderProvider(
            ApplicationContext applicationContext,
            List<DataLoaderInstrumentationExtensionProvider> extensionProviders,
            List<DgsDataLoaderCustomizer> customizers,
            DgsDataLoaderOptionsProvider dataLoaderOptionsProvider,
            ScheduledExecutorService scheduledExecutorService,
            Duration scheduleDuration,
            boolean enableTickerMode) {
        this.applicationContext = applicationContext;
        this.extensionProviders = extensionProviders;
        this.customizers = customizers;
        this.dataLoaderOptionsProvider = dataLoaderOptionsProvider;
        this.scheduledExecutorService = scheduledExecutorService;
        this.scheduleDuration = scheduleDuration;
        this.enableTickerMode = enableTickerMode;
    }

    /** Constructor used by Spring to autowire the provider; discovered beans are injected lazily. */
    @Autowired
    public DefaultDgsDataLoaderProvider(
            ApplicationContext applicationContext,
            ObjectProvider<DataLoaderInstrumentationExtensionProvider> extensionProviders,
            ObjectProvider<DgsDataLoaderCustomizer> customizers,
            ObjectProvider<DgsDataLoaderOptionsProvider> dataLoaderOptionsProvider) {
        this(
                applicationContext,
                extensionProviders.orderedStream().toList(),
                customizers.orderedStream().toList(),
                dataLoaderOptionsProvider.getIfAvailable(DefaultDataLoaderOptionsProvider::new),
                Executors.newSingleThreadScheduledExecutor(),
                Duration.ofMillis(10),
                false);
    }

    public DefaultDgsDataLoaderProvider(
            ApplicationContext applicationContext,
            List<DataLoaderInstrumentationExtensionProvider> extensionProviders) {
        this(
                applicationContext,
                extensionProviders,
                List.of(),
                new DefaultDataLoaderOptionsProvider(),
                Executors.newSingleThreadScheduledExecutor(),
                Duration.ofMillis(10),
                false);
    }

    public DefaultDgsDataLoaderProvider(ApplicationContext applicationContext) {
        this(
                applicationContext,
                List.of(),
                List.of(),
                new DefaultDataLoaderOptionsProvider(),
                Executors.newSingleThreadScheduledExecutor(),
                Duration.ofMillis(10),
                false);
    }

    private record LoaderHolder<T>(T theLoader, DgsDataLoader annotation, String name,
            DispatchPredicate dispatchPredicate) {
    }

    @Override
    public DataLoaderRegistry buildRegistry() {
        return buildRegistryWithContextSupplier(() -> null);
    }

    @Override
    public <T> DataLoaderRegistry buildRegistryWithContextSupplier(Supplier<T> contextSupplier) {
        // We need to set the default predicate to 20ms and individually override with DISPATCH_ALWAYS or the custom
        // dispatch predicate, if specified. The data loader ends up applying the overall dispatch predicate when the
        // custom dispatch predicate is not true otherwise.
        ScheduledDataLoaderRegistry registry = ScheduledDataLoaderRegistry
                .newScheduledRegistry()
                .scheduledExecutorService(scheduledExecutorService)
                .tickerMode(enableTickerMode)
                .schedule(scheduleDuration)
                .dispatchPredicate(DispatchPredicate.DISPATCH_NEVER)
                .build();

        long startTime = System.currentTimeMillis();
        batchLoaders.forEach(holder -> registerDataLoader(holder, registry, contextSupplier, extensionProviders));
        batchLoadersWithContext.forEach(
                holder -> registerDataLoader(holder, registry, contextSupplier, extensionProviders));
        mappedBatchLoaders.forEach(holder -> registerDataLoader(holder, registry, contextSupplier, extensionProviders));
        mappedBatchLoadersWithContext.forEach(
                holder -> registerDataLoader(holder, registry, contextSupplier, extensionProviders));
        if (logger.isDebugEnabled()) {
            logger.debug("Created DGS dataloader registry in {}ms", System.currentTimeMillis() - startTime);
        }
        return registry;
    }

    @PostConstruct
    public void findDataLoaders() {
        addDataLoaderComponents();
        addDataLoaderFields();
    }

    private void addDataLoaderFields() {
        Map<String, Object> dgsComponents = applicationContext.getBeansWithAnnotation(DgsComponent.class);
        for (Object dgsComponent : dgsComponents.values()) {
            Class<?> javaClass = AopUtils.getTargetClass(dgsComponent);

            for (Field field : javaClass.getDeclaredFields()) {
                if (!field.isAnnotationPresent(DgsDataLoader.class)) {
                    continue;
                }
                if (AopUtils.isAopProxy(dgsComponent)) {
                    throw new UnsupportedSecuredDataLoaderException(dgsComponent.getClass());
                }

                DgsDataLoader annotation = field.getAnnotation(DgsDataLoader.class);
                ReflectionUtils.makeAccessible(field);

                if (DgsDataLoader.GENERATE_DATA_LOADER_NAME.equals(annotation.name())) {
                    throw new DgsUnnamedDataLoaderOnFieldException(field);
                }

                try {
                    addDataLoader(
                            field.get(dgsComponent), annotation.name(), dgsComponent.getClass(), annotation, null);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("Could not access method or field: " + e.getMessage(), e);
                }
            }
        }
    }

    private void addDataLoaderComponents() {
        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(DgsDataLoader.class);
        beans.forEach((beanName, beanInstance) -> {
            Class<?> javaClass = AopUtils.getTargetClass(beanInstance);

            // check for class-level annotations
            DgsDataLoader annotation = javaClass.getAnnotation(DgsDataLoader.class);
            if (annotation != null) {
                String dataLoaderName = DataLoaderNameUtil.getDataLoaderName(javaClass, annotation);
                Field predicateField = Arrays.stream(javaClass.getDeclaredFields())
                        .filter(field -> field.isAnnotationPresent(DgsDispatchPredicate.class))
                        .findFirst()
                        .orElse(null);
                if (predicateField != null) {
                    ReflectionUtils.makeAccessible(predicateField);
                    Object dispatchPredicate;
                    try {
                        dispatchPredicate = predicateField.get(beanInstance);
                    } catch (IllegalAccessException e) {
                        throw new IllegalStateException("Could not access method or field: " + e.getMessage(), e);
                    }
                    if (dispatchPredicate instanceof DispatchPredicate predicate) {
                        addDataLoader(beanInstance, dataLoaderName, javaClass, annotation, predicate);
                    }
                } else {
                    addDataLoader(beanInstance, dataLoaderName, javaClass, annotation, null);
                }
            } else if (applicationContext instanceof ConfigurableApplicationContext configurableApplicationContext) {
                // Check for method-level bean annotations in configuration classes
                BeanDefinition beanDefinition =
                        configurableApplicationContext.getBeanFactory().getBeanDefinition(beanName);
                if (beanDefinition.getSource() instanceof StandardMethodMetadata methodMetadata) {
                    Method method = methodMetadata.getIntrospectedMethod();
                    DgsDataLoader methodAnnotation = method.getAnnotation(DgsDataLoader.class);
                    if (methodAnnotation != null) {
                        String dataLoaderName = DataLoaderNameUtil.getDataLoaderName(javaClass, methodAnnotation);
                        addDataLoader(beanInstance, dataLoaderName, javaClass, methodAnnotation, null);
                    }
                }
            }
        });
    }

    private void addDataLoader(
            Object dataLoader,
            String dataLoaderName,
            Class<?> dgsComponentClass,
            DgsDataLoader annotation,
            DispatchPredicate dispatchPredicate) {
        if (dataLoaders.containsKey(dataLoaderName)) {
            throw new MultipleDataLoadersDefinedException(dgsComponentClass, dataLoaders.get(dataLoaderName));
        }
        dataLoaders.put(dataLoaderName, dgsComponentClass);

        Object customizedDataLoader = runCustomizers(dataLoader, dataLoaderName, dgsComponentClass);
        if (customizedDataLoader instanceof BatchLoader<?, ?> loader) {
            batchLoaders.add(new LoaderHolder<>(loader, annotation, dataLoaderName, dispatchPredicate));
        } else if (customizedDataLoader instanceof BatchLoaderWithContext<?, ?> loader) {
            batchLoadersWithContext.add(new LoaderHolder<>(loader, annotation, dataLoaderName, dispatchPredicate));
        } else if (customizedDataLoader instanceof MappedBatchLoader<?, ?> loader) {
            mappedBatchLoaders.add(new LoaderHolder<>(loader, annotation, dataLoaderName, dispatchPredicate));
        } else if (customizedDataLoader instanceof MappedBatchLoaderWithContext<?, ?> loader) {
            mappedBatchLoadersWithContext.add(
                    new LoaderHolder<>(loader, annotation, dataLoaderName, dispatchPredicate));
        } else {
            throw new InvalidDataLoaderTypeException(dgsComponentClass);
        }
    }

    private Object runCustomizers(Object originalDataLoader, String name, Class<?> dgsComponentClass) {
        Object dataLoader = originalDataLoader;
        for (DgsDataLoaderCustomizer customizer : customizers) {
            if (dataLoader instanceof BatchLoader<?, ?> loader) {
                dataLoader = customizer.provide(loader, name);
            } else if (dataLoader instanceof BatchLoaderWithContext<?, ?> loader) {
                dataLoader = customizer.provide(loader, name);
            } else if (dataLoader instanceof MappedBatchLoader<?, ?> loader) {
                dataLoader = customizer.provide(loader, name);
            } else if (dataLoader instanceof MappedBatchLoaderWithContext<?, ?> loader) {
                dataLoader = customizer.provide(loader, name);
            } else {
                throw new InvalidDataLoaderTypeException(dgsComponentClass);
            }
        }
        return dataLoader;
    }

    private DataLoader<?, ?> createDataLoader(
            BatchLoader<?, ?> batchLoader,
            DgsDataLoader dgsDataLoader,
            String dataLoaderName,
            DataLoaderRegistry dataLoaderRegistry,
            Iterable<DataLoaderInstrumentationExtensionProvider> extensionProviders) {
        DataLoaderOptions.Builder options = dataLoaderOptionsProvider.getOptions(dataLoaderName, dgsDataLoader);

        if (batchLoader instanceof DgsDataLoaderRegistryConsumer consumer) {
            consumer.setDataLoaderRegistry(dataLoaderRegistry);
        }

        BatchLoader<?, ?> extendedBatchLoader = wrapBatchLoader(batchLoader, dataLoaderName, extensionProviders);
        return DataLoaderFactory.newDataLoader(dataLoaderName, extendedBatchLoader, options.build());
    }

    private DataLoader<?, ?> createDataLoader(
            MappedBatchLoader<?, ?> batchLoader,
            DgsDataLoader dgsDataLoader,
            String dataLoaderName,
            DataLoaderRegistry dataLoaderRegistry,
            Iterable<DataLoaderInstrumentationExtensionProvider> extensionProviders) {
        DataLoaderOptions.Builder options = dataLoaderOptionsProvider.getOptions(dataLoaderName, dgsDataLoader);

        if (batchLoader instanceof DgsDataLoaderRegistryConsumer consumer) {
            consumer.setDataLoaderRegistry(dataLoaderRegistry);
        }
        MappedBatchLoader<?, ?> extendedBatchLoader =
                wrapMappedBatchLoader(batchLoader, dataLoaderName, extensionProviders);

        return DataLoaderFactory.newMappedDataLoader(extendedBatchLoader, options.build());
    }

    private <T> DataLoader<?, ?> createDataLoader(
            BatchLoaderWithContext<?, ?> batchLoader,
            DgsDataLoader dgsDataLoader,
            String dataLoaderName,
            Supplier<T> supplier,
            DataLoaderRegistry dataLoaderRegistry,
            Iterable<DataLoaderInstrumentationExtensionProvider> extensionProviders) {
        DataLoaderOptions.Builder options = dataLoaderOptionsProvider
                .getOptions(dataLoaderName, dgsDataLoader)
                .setBatchLoaderContextProvider(supplier::get);

        if (batchLoader instanceof DgsDataLoaderRegistryConsumer consumer) {
            consumer.setDataLoaderRegistry(dataLoaderRegistry);
        }

        BatchLoaderWithContext<?, ?> extendedBatchLoader =
                wrapBatchLoaderWithContext(batchLoader, dataLoaderName, extensionProviders);
        return DataLoaderFactory.newDataLoader(dataLoaderName, extendedBatchLoader, options.build());
    }

    private <T> DataLoader<?, ?> createDataLoader(
            MappedBatchLoaderWithContext<?, ?> batchLoader,
            DgsDataLoader dgsDataLoader,
            String dataLoaderName,
            Supplier<T> supplier,
            DataLoaderRegistry dataLoaderRegistry,
            Iterable<DataLoaderInstrumentationExtensionProvider> extensionProviders) {
        DataLoaderOptions.Builder options = dataLoaderOptionsProvider
                .getOptions(dataLoaderName, dgsDataLoader)
                .setBatchLoaderContextProvider(supplier::get);

        if (batchLoader instanceof DgsDataLoaderRegistryConsumer consumer) {
            consumer.setDataLoaderRegistry(dataLoaderRegistry);
        }

        MappedBatchLoaderWithContext<?, ?> extendedBatchLoader =
                wrapMappedBatchLoaderWithContext(batchLoader, dataLoaderName, extensionProviders);
        return DataLoaderFactory.newMappedDataLoader(dataLoaderName, extendedBatchLoader, options.build());
    }

    private void registerDataLoader(
            LoaderHolder<?> holder,
            ScheduledDataLoaderRegistry registry,
            Supplier<?> contextSupplier,
            Iterable<DataLoaderInstrumentationExtensionProvider> extensionProviders) {
        DataLoader<?, ?> loader;
        Object theLoader = holder.theLoader();
        if (theLoader instanceof BatchLoader<?, ?> batchLoader) {
            loader = createDataLoader(batchLoader, holder.annotation(), holder.name(), registry, extensionProviders);
        } else if (theLoader instanceof BatchLoaderWithContext<?, ?> batchLoader) {
            loader = createDataLoader(
                    batchLoader, holder.annotation(), holder.name(), contextSupplier, registry, extensionProviders);
        } else if (theLoader instanceof MappedBatchLoader<?, ?> batchLoader) {
            loader = createDataLoader(batchLoader, holder.annotation(), holder.name(), registry, extensionProviders);
        } else if (theLoader instanceof MappedBatchLoaderWithContext<?, ?> batchLoader) {
            loader = createDataLoader(
                    batchLoader, holder.annotation(), holder.name(), contextSupplier, registry, extensionProviders);
        } else {
            throw new IllegalArgumentException("Data loader " + holder.name() + " has unknown type");
        }
        // detect and throw an exception if multiple data loaders use the same name
        if (registry.getDataLoader(holder.name()) != null) {
            throw new MultipleDataLoadersDefinedException(theLoader.getClass());
        }

        registry.register(
                holder.name(),
                loader,
                holder.dispatchPredicate() != null ? holder.dispatchPredicate() : DispatchPredicate.DISPATCH_ALWAYS);
    }

    private BatchLoader<?, ?> wrapBatchLoader(
            BatchLoader<?, ?> loader,
            String name,
            Iterable<DataLoaderInstrumentationExtensionProvider> extensionProviders) {
        try {
            BatchLoader<?, ?> wrapped = loader;
            for (DataLoaderInstrumentationExtensionProvider provider : extensionProviders) {
                wrapped = provider.provide(wrapped, name);
            }
            return wrapped;
        } catch (NoSuchBeanDefinitionException ex) {
            logger.debug("Unable to wrap the [{} : {}]", name, loader, ex);
            return loader;
        }
    }

    private BatchLoaderWithContext<?, ?> wrapBatchLoaderWithContext(
            BatchLoaderWithContext<?, ?> loader,
            String name,
            Iterable<DataLoaderInstrumentationExtensionProvider> extensionProviders) {
        try {
            BatchLoaderWithContext<?, ?> wrapped = loader;
            for (DataLoaderInstrumentationExtensionProvider provider : extensionProviders) {
                wrapped = provider.provide(wrapped, name);
            }
            return wrapped;
        } catch (NoSuchBeanDefinitionException ex) {
            logger.debug("Unable to wrap the [{} : {}]", name, loader, ex);
            return loader;
        }
    }

    private MappedBatchLoader<?, ?> wrapMappedBatchLoader(
            MappedBatchLoader<?, ?> loader,
            String name,
            Iterable<DataLoaderInstrumentationExtensionProvider> extensionProviders) {
        try {
            MappedBatchLoader<?, ?> wrapped = loader;
            for (DataLoaderInstrumentationExtensionProvider provider : extensionProviders) {
                wrapped = provider.provide(wrapped, name);
            }
            return wrapped;
        } catch (NoSuchBeanDefinitionException ex) {
            logger.debug("Unable to wrap the [{} : {}]", name, loader, ex);
            return loader;
        }
    }

    private MappedBatchLoaderWithContext<?, ?> wrapMappedBatchLoaderWithContext(
            MappedBatchLoaderWithContext<?, ?> loader,
            String name,
            Iterable<DataLoaderInstrumentationExtensionProvider> extensionProviders) {
        try {
            MappedBatchLoaderWithContext<?, ?> wrapped = loader;
            for (DataLoaderInstrumentationExtensionProvider provider : extensionProviders) {
                wrapped = provider.provide(wrapped, name);
            }
            return wrapped;
        } catch (NoSuchBeanDefinitionException ex) {
            logger.debug("Unable to wrap the [{} : {}]", name, loader, ex);
            return loader;
        }
    }
}
