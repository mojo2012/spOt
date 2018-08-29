package io.spotnext.core.infrastructure.support.init;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Properties;

/**
 * <p>ModuleConfig class.</p>
 *
 * @author mojo2012
 * @version 1.0
 * @since 1.0
 */
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface ModuleConfig {
	/**
	 * Returns the {@link Properties} for current {@link ModuleInit}.
	 * 
	 */
	String appConfigFile() default "";

	/**
	 * Returns the spring configuration file.
	 * 
	 */
	String springConfigFile() default "";

	/**
	 * Returns the spring configuration class. This overrides the
	 * {@link ModuleConfig#springConfigFile()} property.
	 * 
	 */
	Class<?> springConfigClass() default ModuleInit.class;

	/**
	 * Defines the unique module name.
	 */
	String moduleName();

	/**
	 * Defines the scan paths for item models.
	 */
	String[] modelPackagePaths();
}
