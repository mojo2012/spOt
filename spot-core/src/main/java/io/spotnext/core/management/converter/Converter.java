package io.spotnext.core.management.converter;

import org.springframework.core.convert.converter.ConditionalConverter;

/**
 * A converter converts a source object of type {@code S} to a target of type
 * {@code T}.
 *
 * <p>
 * Implementations of this interface are thread-safe and can be shared.
 *
 * <p>
 * Implementations may additionally implement {@link org.springframework.core.convert.converter.ConditionalConverter}.
 *
 * @author Keith Donald
 * @since 3.0
 * @param <S>
 *            the source type
 * @param <T>
 *            the target type
 * @version 1.0
 */
public interface Converter<S, T> {

	/**
	 * Convert the source object of type {@code S} to target type {@code T}.
	 *
	 * @param source
	 *            the source object to convert, which must be an instance of
	 *            {@code S} (never {@code null})
	 * @return the converted object, which must be an instance of {@code T}
	 *         (potentially {@code null})
	 * @throws java.lang.IllegalArgumentException
	 *             if the source cannot be converted to the desired target type
	 * @since 1.0
	 */
	T convert(S source);
}
