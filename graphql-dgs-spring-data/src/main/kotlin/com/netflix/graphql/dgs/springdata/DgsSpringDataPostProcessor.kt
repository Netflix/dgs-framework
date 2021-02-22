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

package com.netflix.graphql.dgs.springdata

import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor
import java.util.*


class DgsSpringDataPostProcessor : BeanDefinitionRegistryPostProcessor {

    companion object {
        /*
        /**
	 * Returns the resolved class of a given bean fetched through the bean factory by its
	 * name. If the class can't be loaded, i.e. its not present in the classpath it will
	 * return empty.
	 */
	@NonNull
	public static Optional<BeanDefinitionType> getOptionalBeanDefinitionType(final String beanName,
			final ConfigurableListableBeanFactory beanFactory) {
		final BeanDefinition beanDefinition = beanFactory.getBeanDefinition(beanName);
		return getOptionalBeanDefinitionClass(beanName, beanDefinition)
				.map(aClass -> new BeanDefinitionType(beanName, beanDefinition, aClass));
	}
	/**
	 * Returns the {@link Class}, if available, as defined by the {@link BeanDefinition}
	 * wrapped in an {@link Optional} else an {@link Optional#empty()}.
	 * @param beanName Name of the bean as defined in the registry.
	 * @param beanDefinition Bean Definition that will provide the class.
	 */
	public static Optional<Class<?>> getOptionalBeanDefinitionClass(String beanName, BeanDefinition beanDefinition) {
		try {
			Class<?> beanClass = getBeanDefinitionClass(beanName, beanDefinition);
			return Optional.ofNullable(beanClass);
		}
		catch (IllegalStateException | ClassNotFoundException error) {
			logger.debug("Unable to resolve the class for bean {} with definition {}", beanName, beanDefinition, error);
			return Optional.empty();
		}
		catch (RuntimeException error) {
			logger.error("Unable to resolve the class for bean {} with definition {}", beanName, beanDefinition, error);
			return Optional.empty();
		}
	}
         */

    }
    fun getOptionalBeanDefinitionType(beanName: String, beanFactory: BeanDefinitionRegistry): Optional<BeanDefinitionType> {
        return Optional.empty()
    }

    override fun postProcessBeanDefinitionRegistry(registry: BeanDefinitionRegistry){
//        val stream: Stream<String> = Arrays. of(registry.beanDefinitionNames)
//        stream
//                .map{ name:String -> getOptionalBeanDefinitionType(name, registry) }
//                .filter{Optional::isPresent}
//                .flatMap{ c -> c.get().beanClass.isAnnotationPresent( DgsSpringDataConfiguration::class)}
//                .collect{ Collectors.toSet()}
/*
* Set<BeanAnnotatedReference<RequiresLeadership>> set = Stream.of(registry.getBeanDefinitionNames())
				.map(name -> getOptionalBeanDefinitionType(name, registry)).filter(Optional::isPresent)
				.map(Optional::get).flatMap(this::streamsOfAnnotatedReferences).collect(Collectors.toSet());

		annotatedReferences.addAll(set);
* */
    }


    override fun postProcessBeanFactory(beanFactory: ConfigurableListableBeanFactory){
        //no-op
    }
}


data class BeanDefinitionType(val beanName:String, val beanClass:Class<*>, val beanDefinition:BeanDefinition? = null)
/*
/**
 * Represents a named bean and provides its resolved class along with its definition as
 * provided by the Spring Registry.
 */
@NetflixSpringBootInternal()
public class BeanDefinitionType {

	private final String beanName;

	private final Class<?> beanClass;

	private final BeanDefinition beanDefinition;

	BeanDefinitionType(String beanName, BeanDefinition beanDefinition, Class<?> beanClass) {
		this.beanName = beanName;
		this.beanClass = beanClass;
		this.beanDefinition = beanDefinition;
	}

	public String getBeanName() {
		return beanName;
	}

	public boolean hasName() {
		return !StringUtils.isEmpty(getBeanName());
	}

	public Class<?> getBeanClass() {
		return beanClass;
	}

	public BeanDefinition getBeanDefinition() {
		return beanDefinition;
	}

	/**
	 * Returns true if this bean's class if of type {@link FactoryBean} and its
	 * {@link FactoryBean#getObject()} returns a type that is the same as the given
	 * {@code testClass} or where {@code testClass} is a super type of it.
	 */
	public <T> boolean isFactoryBeanFor(Class<T> testClass) {
		if (!FactoryBean.class.isAssignableFrom(getBeanClass())) {
			return false;
		}
		//
		Optional<Method> optionalMethod = Arrays.stream(ReflectionUtils.getAllDeclaredMethods(getBeanClass()))
				.filter(method -> method.getParameterCount() == 0 && "getObject".equals(method.getName())
						&& testClass.isAssignableFrom(method.getReturnType()))
				.findFirst();
		return optionalMethod.isPresent();
	}

	public boolean isAssignable(Class<?> testClass) {
		return testClass.isAssignableFrom(getBeanClass());
	}

}
*
* */