package com.netflix.graphql.dgs

import com.netflix.graphql.dgs.exceptions.InvalidDataLoaderTypeException
import com.netflix.graphql.dgs.internal.DgsDataLoaderProvider
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.context.ApplicationContext

@ExtendWith(MockKExtension::class)
class DgsDataLoaderProviderTest {
    @MockK
    lateinit var applicationContextMock: ApplicationContext

    @Test
    fun findDataLoaders() {
        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java)} returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDataLoader::class.java)} returns mapOf(Pair("helloFetcher", ExampleBatchLoader()))
        every { applicationContextMock.getBean(DataLoaderInstrumentationExtensionProvider::class.java)} throws NoSuchBeanDefinitionException(DataLoaderInstrumentationExtensionProvider::class.java)

        val provider = DgsDataLoaderProvider(applicationContextMock)
        provider.findDataLoaders()
        val dataLoaderRegistry = provider.buildRegistry()
        Assertions.assertEquals(1, dataLoaderRegistry.dataLoaders.size)
        val dataLoader = dataLoaderRegistry .getDataLoader<Any, Any>("exampleLoader")
        Assertions.assertNotNull(dataLoader)
    }

    @Test
    fun dataLoaderInvalidType() {
        every { applicationContextMock.getBeansWithAnnotation(DgsDataLoader::class.java)} returns mapOf(Pair("helloFetcher", object {}))
        val provider = DgsDataLoaderProvider(applicationContextMock)
        assertThrows<InvalidDataLoaderTypeException> { provider.findDataLoaders() }
    }

    @Test
    fun findDataLoadersFromFields() {
        every { applicationContextMock.getBeansWithAnnotation(DgsDataLoader::class.java)} returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java)} returns mapOf(Pair("helloFetcher", ExampleBatchLoaderFromField()))
        every { applicationContextMock.getBean(DataLoaderInstrumentationExtensionProvider::class.java)} throws NoSuchBeanDefinitionException(DataLoaderInstrumentationExtensionProvider::class.java)

        val provider = DgsDataLoaderProvider(applicationContextMock)
        provider.findDataLoaders()
        val dataLoaderRegistry = provider.buildRegistry()
        Assertions.assertEquals(2, dataLoaderRegistry.dataLoaders.size)
        val dataLoader = dataLoaderRegistry.getDataLoader<Any, Any>("exampleLoaderFromField")
        Assertions.assertNotNull(dataLoader)

        val privateDataLoader = dataLoaderRegistry.getDataLoader<Any, Any>("privateExampleLoaderFromField")
        Assertions.assertNotNull(privateDataLoader)
    }

    @Test
    fun findMappedDataLoaders() {
        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java)} returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDataLoader::class.java)} returns mapOf(Pair("helloFetcher", ExampleMappedBatchLoader()))
        every { applicationContextMock.getBean(DataLoaderInstrumentationExtensionProvider::class.java)} throws NoSuchBeanDefinitionException(DataLoaderInstrumentationExtensionProvider::class.java)

        val provider = DgsDataLoaderProvider(applicationContextMock)
        provider.findDataLoaders()
        val dataLoaderRegistry = provider.buildRegistry()
        Assertions.assertEquals(1, dataLoaderRegistry.dataLoaders.size)
        val dataLoader = dataLoaderRegistry .getDataLoader<Any, Any>("exampleMappedLoader")
        Assertions.assertNotNull(dataLoader)
    }

    @Test
    fun findMappedDataLoadersFromFields() {
        every { applicationContextMock.getBeansWithAnnotation(DgsDataLoader::class.java)} returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java)} returns mapOf(Pair("helloFetcher", ExampleMappedBatchLoaderFromField()))
        every { applicationContextMock.getBean(DataLoaderInstrumentationExtensionProvider::class.java)} throws NoSuchBeanDefinitionException(DataLoaderInstrumentationExtensionProvider::class.java)

        val provider = DgsDataLoaderProvider(applicationContextMock)
        provider.findDataLoaders()
        val dataLoaderRegistry = provider.buildRegistry()
        Assertions.assertEquals(2, dataLoaderRegistry.dataLoaders.size)
        val dataLoader = dataLoaderRegistry.getDataLoader<Any, Any>("exampleMappedLoaderFromField")
        Assertions.assertNotNull(dataLoader)

        val privateDataLoader = dataLoaderRegistry.getDataLoader<Any, Any>("privateExampleMappedLoaderFromField")
        Assertions.assertNotNull(privateDataLoader)
    }
}