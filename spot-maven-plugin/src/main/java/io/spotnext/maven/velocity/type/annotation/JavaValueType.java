package io.spotnext.maven.velocity.type.annotation;

import java.math.BigDecimal;

/**
 * <p>JavaValueType class.</p>
 *
 * @since 1.0
 */
public enum JavaValueType {
	STRING, LITERAL, CLASS, ENUM_VALUE, STRING_ARRAY, LITERAL_ARRAY, NULL;

	/**
	 * <p>forType.</p>
	 *
	 * @param type a {@link java.lang.Class} object.
	 * @return a {@link io.spotnext.maven.velocity.type.annotation.JavaValueType} object.
	 */
	public static JavaValueType forType(final Class<?> type) {
		if (Integer.class.isAssignableFrom(type) || Long.class.isAssignableFrom(type)
				|| Short.class.isAssignableFrom(type) || Byte.class.isAssignableFrom(type)
				|| Float.class.isAssignableFrom(type) || Double.class.isAssignableFrom(type)
				|| BigDecimal.class.isAssignableFrom(type) || Boolean.class.isAssignableFrom(type)) {
			return LITERAL;
		} else if (String.class.isAssignableFrom(type)) {
			return STRING;
		} else if (String[].class.isAssignableFrom(type)) {
			return STRING_ARRAY;
		} else if (Class.class.isAssignableFrom(type)) {
			return CLASS;
		} else if (Enum.class.isAssignableFrom(type)) {
			return ENUM_VALUE;
		}

		return null;
	}

	/**
	 * <p>forValue.</p>
	 *
	 * @param instance a {@link java.lang.Object} object.
	 * @return a {@link io.spotnext.maven.velocity.type.annotation.JavaValueType} object.
	 */
	public static JavaValueType forValue(final Object instance) {
		if (instance instanceof Integer || instance instanceof Long || instance instanceof Short
				|| instance instanceof Byte || instance instanceof Float || instance instanceof Double
				|| instance instanceof BigDecimal || instance instanceof Boolean) {
			return LITERAL;
		} else if (instance instanceof String) {
			return STRING;
		} else if (instance instanceof String[]) {
			return STRING_ARRAY;
		} else if (instance instanceof Class<?>) {
			return CLASS;
		} else if (instance instanceof Enum<?>) {
			return ENUM_VALUE;
		}

		return null;
	}
}
