package io.spotnext.infrastructure.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.spotnext.infrastructure.type.AccessorType;

/**
 * Defines an item property. Without this annotation, the values are not stored
 * in the database.
 */
@Target(ElementType.METHOD)
@Inherited
@Retention(RetentionPolicy.RUNTIME)
public @interface Accessor {

	/**
	 * @return the type of the accessor.
	 */
	public AccessorType type();

	/**
	 * @return the name of the property
	 */
	public String propertyName();
}
