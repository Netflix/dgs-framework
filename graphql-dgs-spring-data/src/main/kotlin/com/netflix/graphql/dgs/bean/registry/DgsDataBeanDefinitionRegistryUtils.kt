/*
 * Copyright 2021 Netflix, Inc.
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

package com.netflix.graphql.dgs.bean.registry

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.core.annotation.AnnotationUtils
import org.springframework.core.type.MethodMetadata
import org.springframework.core.type.StandardMethodMetadata
import org.springframework.core.type.filter.AnnotationTypeFilter
import org.springframework.util.ClassUtils
import java.lang.annotation.ElementType
import java.lang.reflect.Method
import java.util.*
import java.util.stream.Stream
import org.springframework.util.ReflectionUtils as SpringReflectionUtils


object DgsDataBeanDefinitionRegistryUtils {

    private val logger = LoggerFactory.getLogger(DgsDataBeanDefinitionRegistryUtils::class.java)

    @FunctionalInterface
    fun interface ClassAnnotationHandler<T : Annotation> {
        fun handle(annotation: T, clazz: Class<*>)
    }

    @FunctionalInterface
    fun interface MethodAnnotationHandler<T : Annotation> {
        fun handle(annotation: T, method: Method)
    }

    open class BeanDefinitionType internal constructor(val beanName: String, val beanClass: Class<*>, val beanDefinition: BeanDefinition)


    fun getOptionalBeanDefinitionType(beanName: String,
                                      beanFactory: BeanDefinitionRegistry): Optional<BeanDefinitionType> {
        val beanDefinition: BeanDefinition = beanFactory.getBeanDefinition(beanName)
        return getOptionalBeanDefinitionClass(beanName, beanDefinition)
                .map{ aClass: Class<*> -> BeanDefinitionType(beanName, aClass,  beanDefinition) }
    }

    fun getOptionalBeanDefinitionClass(beanName: String, beanDefinition: BeanDefinition): Optional<Class<*>> {
        try {
            val beanClass = getBeanDefinitionClass(beanName, beanDefinition)
            return Optional.ofNullable(beanClass)
        } catch (error: IllegalStateException) {
            logger.debug("Unable to resolve the class for bean {} with definition {}", beanName, beanDefinition, error)
            return Optional.empty();
        } catch (error: ClassNotFoundException) {
            logger.debug("Unable to resolve the class for bean {} with definition {}", beanName, beanDefinition, error)
            return Optional.empty();
        } catch (error: RuntimeException) {
            logger.error("Unable to resolve the class for bean {} with definition {}", beanName, beanDefinition, error)
            return Optional.empty();
        }
    }

    /**
     * Return a [Stream] of wrapped annotation references, in a [BeanAnnotatedReference], given a [BeanDefinitionType].
     * The `BeanAnnotatedReference` keeps the context of the bean's name, its resolved `Class`, the annotation reference, and method.
     */
    fun <T : Annotation> streamsOfAnnotatedClassReferences(registry: BeanDefinitionRegistry,
                                                           annotationClass: Class<T>): Stream<AnnotatedBeanClassReference<T>> {

        return Arrays.stream(registry.beanDefinitionNames)
                .map { name: String -> getOptionalBeanDefinitionType(name, registry) }
                .filter{ it.isPresent }
                .map { optBean ->
                    val bd = optBean.get()
                    val annotation = AnnotationUtils.findAnnotation(bd.beanClass, annotationClass)

                    if (annotation == null)
                        Optional.empty<AnnotatedBeanClassReference<T>>()
                    else
                        Optional.of(
                                AnnotatedBeanClassReference(
                                        beanName = bd.beanName,
                                        beanClass = bd.beanClass,
                                        beanDefinition = bd.beanDefinition,
                                        annotationReference = annotation))

                }.map { it.get() }
    }


//    /**
//	 * Returns the resolved class of a given bean fetched through the bean factory by its
//	 * name. If the class can't be loaded, i.e. its not present in the classpath it will
//	 * return empty.
//	 */
//	@NonNull
//	public static Optional<BeanDefinitionType> getOptionalBeanDefinitionType(final String beanName,
//			final ConfigurableListableBeanFactory beanFactory) {
//		final BeanDefinition beanDefinition = beanFactory.getBeanDefinition(beanName);
//		return getOptionalBeanDefinitionClass(beanName, beanDefinition)
//				.map(aClass -> new BeanDefinitionType(beanName, beanDefinition, aClass));
//	}



    @Throws(ClassNotFoundException::class, LinkageError::class)
    fun getBeanDefinitionClass(beanName: String, beanDefinition: BeanDefinition): Class<*>? {
        var beanClass: Class<*>? = null
        try {
            if (beanDefinition.beanClassName != null) {
                beanClass = ClassUtils.forName(beanDefinition.beanClassName,
                        DgsDataBeanDefinitionRegistryUtils::class.java.getClassLoader())
            } else {
                if (beanDefinition is AnnotatedBeanDefinition) {
                    val factoryMethodMetadata: MethodMetadata = (beanDefinition as AnnotatedBeanDefinition).getFactoryMethodMetadata()
                    Objects.requireNonNull(factoryMethodMetadata,
                            "No suitable factory method for beanDefinition for bean: $beanName")
                    beanClass = if (factoryMethodMetadata is StandardMethodMetadata) {
                        (factoryMethodMetadata as StandardMethodMetadata).getIntrospectedMethod()
                                .getReturnType()
                    } else {
                        ClassUtils.forName(factoryMethodMetadata.getReturnTypeName(),
                                DgsDataBeanDefinitionRegistryUtils::class.java.getClassLoader())
                    }
                }
            }
            Objects.requireNonNull(beanClass, "Could not locate class for bean $beanName")
        } catch (error: ClassNotFoundException) {
            throw ClassNotFoundException("Failed to resolve the class for bean $beanName", error)
        } catch (error: LinkageError) {
            throw LinkageError("Failed to instantiate the class for bean $beanName", error)
        } catch (e: Exception) {
            throw RuntimeException("Failed to resolve the class for bean $beanName", e)
        }
        return beanClass
    }


    /**
     * Return a [Stream] of wrapped annotation references, in a [BeanAnnotatedReference], given a [BeanDefinitionType].
     * The
     * {@code BeanAnnotatedReference} keeps the context of the bean's name, its resolved
     * {@code Class}, the annotation reference, and method.
     */
    fun <T : Annotation> streamsOfAnnotatedMethodReferences(beanDefinitionType: BeanDefinitionType,
                                                            annotationClass: Class<T>): Stream<BeanMethodAnnotatedReference<T>> {
        val annotatedReferences: LinkedHashSet<BeanMethodAnnotatedReference<T>> = LinkedHashSet()
        introspectMethodAnnotation(beanDefinitionType.beanClass, annotationClass) { annotation, method ->
            annotatedReferences.add(
                    BeanMethodAnnotatedReference(
                            beanDefinitionType.beanName,
                            beanDefinitionType.beanClass,
                            annotation,
                            beanDefinitionType.beanDefinition,
                            method))
        }
        return annotatedReferences.stream()
    }

    /**
     * Introspects the given `beanClass` for methods annotated with the given `annotationClass`.
     * For every method found it will call the `annotationHandler`.
     *
     * @param beanClass Class to introspect.
     * @param annotationClass The annotation class that we will look for at the method level.
     * @param annotationHandler The handler/function that will be called per method found annotated by the `annotationClass`.
     */
    fun <T : Annotation> introspectClassMethodsAnnotation(beanClass: Class<*>,
                                                          annotationClass: Class<T>,
                                                          annotationHandler: MethodAnnotationHandler<T>) {
        SpringReflectionUtils
                .doWithMethods(beanClass) { method ->
                    Arrays.stream(method.annotations)
                            .filter { annotation -> annotationClass.isAssignableFrom(annotation.annotationClass::class.java) }
                            .map { annotationClass.cast(it) }
                            .forEach { annotationHandler.handle(it, method) }
                }
    }

    /**
     * Introspects the given `beanClass` for methods annotated with the given `annotationClass`.
     * For every method found it will call the `annotationHandler`.
     *
     * @param beanClass Class to introspect.
     * @param annotationClass The annotation class that we will look for at the method level.
     * @param annotationHandler The handler/function that will be called per method found annotated by the `annotationClass`.
     */
    fun <T : Annotation> introspectMethodAnnotation(beanClass: Class<*>,
                                                    annotationClass: Class<T>,
                                                    annotationHandler: MethodAnnotationHandler<T>) {
        SpringReflectionUtils
                .doWithMethods(beanClass) { method ->
                    Arrays.stream(method.annotations)
                            .filter { annotation -> annotationClass.isAssignableFrom(annotation.annotationClass::class.java) }
                            .map { annotationClass.cast(it) }
                            .forEach { annotationHandler.handle(it, method) }
                }
    }


}

interface BeanAnnotatedReference<Annotation_T : Annotation?> {
    /** Name of the bean in Springs Registry.  */
    val beanName: String?

    /** The resolved type of the bean as provided by its bean definition.  */
    val beanClass: Class<*>?

    /** Reference to the annotation.  */
    val annotationReference: Annotation_T

    /**
     * Returns the type of element this annotation reference was associated with, e.g.
     * [ElementType.METHOD] for a method annotated by the given annotation type
     * represented by `Annotation_T`.
     */
    val elementType: ElementType?

    /** The bean definition that defining this component. */
    val beanDefinition: BeanDefinition?
}

abstract class AbstractBeanAnnotatedReference<Annotation_T : Annotation?>(
        /** {@inheritDoc}  */
        override val beanName: String,
        /** {@inheritDoc}  */
        override val beanClass: Class<*>,
        /** {@inheritDoc}  */
        override val annotationReference: Annotation_T,
        /** {@inheritDoc} */
        override val beanDefinition: BeanDefinition
) : BeanAnnotatedReference<Annotation_T> {

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o !is AbstractBeanAnnotatedReference<*>) return false
        val that = o
        return beanName == that.beanName && beanClass == that.beanClass && annotationReference == that.annotationReference && beanDefinition == this.beanDefinition
    }

    override fun hashCode(): Int {
        return Objects.hash(beanName, beanClass, annotationReference, beanDefinition)
    }

    override fun toString(): String {
        return javaClass.name + "{" + toStringFields() + '}'
    }

    fun toStringFields(): String {
        return ("beanName='" + beanName + '\'' + ", beanClass=" + beanClass + ", annotationReference=" + annotationReference)
    }
}


/**
 * Represents a method of a bean that has been annotated with a given annotation class.
 */
class BeanMethodAnnotatedReference<Annotation_T : Annotation?> internal constructor(
        beanName: String?,
        beanClass: Class<*>?,
        annotationReference: Annotation_T,
        beanDefinition: BeanDefinition,
        /** Reference to the method annotated with `annotationReference`.  */
        val method: Method,
) : AbstractBeanAnnotatedReference<Annotation_T>(beanName!!, beanClass!!, annotationReference, beanDefinition) {

    /** Returns the value of [ElementType.METHOD].  */
    override val elementType: ElementType
        get() = ElementType.METHOD

    override fun toString(): String {
        return "BeanMethodAnnotatedReference{" + "method=" + method + ", " + super.toStringFields() + "} "
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o !is BeanMethodAnnotatedReference<*>) return false
        if (!super.equals(o)) return false
        return method == o.method
    }

    override fun hashCode(): Int {
        return Objects.hash(super.hashCode(), method)
    }
}

/**
 * Represents a method of a bean that has been annotated with a given annotation class.
 */
class AnnotatedBeanClassReference<Annotation_T : Annotation?> internal constructor(
        beanName: String?,
        beanClass: Class<*>?,
        beanDefinition: BeanDefinition,
        annotationReference: Annotation_T
) : AbstractBeanAnnotatedReference<Annotation_T>(beanName!!, beanClass!!, annotationReference, beanDefinition) {

    /** Returns the value of [ElementType.METHOD].  */
    override val elementType: ElementType
        get() = ElementType.TYPE

    override fun toString(): String {
        return "BeanClassAnnotatedReference{" + super.toStringFields() + "} "
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o !is AnnotatedBeanClassReference<*>) return false
        if (!super.equals(o)) return false

        return true
    }
}
