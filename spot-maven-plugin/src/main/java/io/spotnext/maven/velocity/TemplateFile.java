package io.spotnext.maven.velocity;

import static java.lang.annotation.ElementType.TYPE;

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>TemplateFile class.</p>
 *
 * @since 1.0
 */
@Documented
@Target(TYPE)
@Inherited
@Retention(RetentionPolicy.RUNTIME)
public @interface TemplateFile {

	/**
	 * This is the file name of the template file. It has to be on the classpath.
	 */
	String value();
}
