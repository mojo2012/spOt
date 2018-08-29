package io.spotnext.core.support.util;

import java.lang.reflect.Modifier;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.config.ConstructorArgumentValues.ValueHolder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;

/**
 * <p>SpringUtil class.</p>
 *
 * @since 1.0
 * @author mojo2012
 * @version 1.0
 */
public class SpringUtil {

	public enum BeanScope {
		prototype, singleton,
	}

	/**
	 * Registers a new bean of the given type in the given spring context.
	 *
	 * @param type a {@link java.lang.Class} object.
	 * @param beanId
	 *            if this is not empty it will override the default bean id
	 * @param scope a {@link io.spotnext.core.support.util.SpringUtil.BeanScope} object.
	 * @param beanFactory a {@link org.springframework.beans.factory.support.BeanDefinitionRegistry} object.
	 * @param alias a {@link java.lang.String} object.
	 * @param constructorArguments a {@link java.util.List} object.
	 * @param lazyInit a boolean.
	 */
	public static void registerBean(final BeanDefinitionRegistry beanFactory, final Class<?> type, final String beanId,
			final String alias, final BeanScope scope, final List<? extends Object> constructorArguments,
			final boolean lazyInit) {

		final GenericBeanDefinition beanDefinition = new GenericBeanDefinition();
		beanDefinition.setBeanClass(type);
		beanDefinition.setLazyInit(lazyInit);
		beanDefinition.setAbstract(Modifier.isAbstract(type.getModifiers()));
		beanDefinition.setAutowireCandidate(true);

		if (CollectionUtils.isNotEmpty(constructorArguments)) {
			final ConstructorArgumentValues constructorArgs = new ConstructorArgumentValues();

			for (final Object o : constructorArguments) {
				constructorArgs.addGenericArgumentValue(new ValueHolder(o));
			}

			beanDefinition.setConstructorArgumentValues(constructorArgs);
		}

		String id = type.getSimpleName();

		// use the annotated itemtype name, it should
		if (StringUtils.isNotBlank(beanId)) {
			id = beanId;
		}

		if (scope != null) {
			beanDefinition.setScope(scope.toString());
		}

		beanFactory.registerBeanDefinition(id, beanDefinition);

		if (StringUtils.isNotBlank(alias)) {
			registerAlias(beanFactory, id, alias);
		}
	}

	/**
	 * <p>registerAlias.</p>
	 *
	 * @param beanFactory a {@link org.springframework.beans.factory.support.BeanDefinitionRegistry} object.
	 * @param beanId a {@link java.lang.String} object.
	 * @param alias a {@link java.lang.String} object.
	 */
	public static void registerAlias(final BeanDefinitionRegistry beanFactory, final String beanId,
			final String alias) {

		beanFactory.registerAlias(beanId, alias);
	}
}
