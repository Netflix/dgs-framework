package com.netflix.graphql.dgs.metrics.micrometer.dataloader

import com.netflix.graphql.dgs.DataLoaderInstrumentationExtensionProvider
import com.netflix.graphql.dgs.metrics.micrometer.DgsMeterRegistrySupplier
import net.bytebuddy.ByteBuddy
import net.bytebuddy.implementation.MethodDelegation
import net.bytebuddy.implementation.bind.annotation.Pipe
import net.bytebuddy.matcher.ElementMatchers
import org.dataloader.BatchLoader
import org.dataloader.BatchLoaderWithContext
import org.dataloader.MappedBatchLoader
import org.dataloader.MappedBatchLoaderWithContext
import java.lang.reflect.Constructor

class DgsDataLoaderInstrumentationProvider(
    private val meterRegistrySupplier: DgsMeterRegistrySupplier
) : DataLoaderInstrumentationExtensionProvider {

    private val batchLoaderClasses = mutableMapOf<String, Constructor<out BatchLoader<*, *>>>()
    private val batchLoaderWithContextClasses = mutableMapOf<String, Constructor<out BatchLoaderWithContext<*, *>>>()
    private val mappedBatchLoaderClasses = mutableMapOf<String, Constructor<out MappedBatchLoader<*, *>>>()
    private val mappedBatchLoaderWithContextClasses = mutableMapOf<String, Constructor<out MappedBatchLoaderWithContext<*, *>>>()

    override fun provide(original: BatchLoader<*, *>, name: String): BatchLoader<*, *> {
        return batchLoaderClasses.getOrPut(name) {
            val withBinders =
                MethodDelegation
                    .withDefaultConfiguration()
                    .withBinders(Pipe.Binder.install(Forwarder::class.java))
                    .to(BatchLoaderInterceptor(original, name, meterRegistrySupplier.get()))
            ByteBuddy()
                .subclass(BatchLoader::class.java)
                .method(ElementMatchers.named("load"))
                .intercept(withBinders)
                .make()
                .load(javaClass.classLoader)
                .loaded
                .getDeclaredConstructor()
        }.newInstance()
    }

    override fun provide(original: BatchLoaderWithContext<*, *>, name: String): BatchLoaderWithContext<*, *> {
        return batchLoaderWithContextClasses.getOrPut(name) {
            val withBinders = MethodDelegation.withDefaultConfiguration()
                .withBinders(Pipe.Binder.install(Forwarder::class.java))
                .to(BatchLoaderWithContextInterceptor(original, name, meterRegistrySupplier.get()))

            ByteBuddy()
                .subclass(BatchLoaderWithContext::class.java)
                .method(ElementMatchers.named("load"))
                .intercept(withBinders)
                .make()
                .load(javaClass.classLoader)
                .loaded
                .getDeclaredConstructor()
        }.newInstance()
    }

    override fun provide(original: MappedBatchLoader<*, *>, name: String): MappedBatchLoader<*, *> {
        return mappedBatchLoaderClasses.getOrPut(name) {
            val withBinders = MethodDelegation.withDefaultConfiguration()
                .withBinders(Pipe.Binder.install(Forwarder::class.java)).to(
                    MappedBatchLoaderInterceptor(original, name, meterRegistrySupplier.get())
                )

            ByteBuddy()
                .subclass(MappedBatchLoader::class.java)
                .method(ElementMatchers.named("load"))
                .intercept(withBinders)
                .make()
                .load(javaClass.classLoader)
                .loaded
                .getDeclaredConstructor()
        }.newInstance()
    }

    override fun provide(
        original: MappedBatchLoaderWithContext<*, *>,
        name: String
    ): MappedBatchLoaderWithContext<*, *> {
        return mappedBatchLoaderWithContextClasses.getOrPut(name) {
            val withBinders = MethodDelegation.withDefaultConfiguration()
                .withBinders(Pipe.Binder.install(Forwarder::class.java))
                .to(MappedBatchLoaderWithContextInterceptor(original, name, meterRegistrySupplier.get()))
            ByteBuddy()
                .subclass(MappedBatchLoaderWithContext::class.java)
                .method(ElementMatchers.named("load"))
                .intercept(withBinders)
                .make()
                .load(javaClass.classLoader)
                .loaded
                .getDeclaredConstructor()
        }.newInstance()
    }
}
