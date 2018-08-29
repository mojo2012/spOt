package io.spotnext.core.infrastructure.strategy.impl;

import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.apache.commons.lang.NotImplementedException;
import org.apache.commons.lang3.SerializationException;
import org.springframework.stereotype.Service;

import io.spotnext.core.infrastructure.strategy.SerializationStrategy;

/**
 * <p>DefaultXmlSerializationStrategy class.</p>
 *
 * @author mojo2012
 * @version 1.0
 * @since 1.0
 */
@Service
public class DefaultXmlSerializationStrategy implements SerializationStrategy {

	private boolean prettyPrint = true;

	/** {@inheritDoc} */
	@Override
	public <T> String serialize(final T object) throws SerializationException {
		if (object == null) {
			return null;
		}

		String xmlString = "";

		try {
			final JAXBContext context = JAXBContext.newInstance(object.getClass());
			final Marshaller m = context.createMarshaller();

			m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, isPrettyPrint());

			final StringWriter sw = new StringWriter();
			m.marshal(object, sw);
			xmlString = sw.toString();
		} catch (final JAXBException e) {
			throw new SerializationException("Cannot serialize object", e);
		}

		return xmlString;
	}

	/** {@inheritDoc} */
	@Override
	public <T> T deserialize(final String serializedObject, final Class<T> type) throws SerializationException {
		T object = null;

		try {
			final JAXBContext context = JAXBContext.newInstance(type);
			final Unmarshaller m = context.createUnmarshaller();

			object = (T) m.unmarshal(new StringReader(serializedObject));
		} catch (final JAXBException e) {
			throw new SerializationException("Cannot deserialize object", e);
		}

		return object;
	}

	/** {@inheritDoc} */
	@Override
	public <T> T deserialize(final String serializedObject, final T instanceToUpdate) throws SerializationException {
		throw new NotImplementedException();
	}

	/**
	 * <p>isPrettyPrint.</p>
	 *
	 * @return a boolean.
	 */
	public boolean isPrettyPrint() {
		return prettyPrint;
	}

	/**
	 * <p>Setter for the field <code>prettyPrint</code>.</p>
	 *
	 * @param prettyPrint a boolean.
	 */
	public void setPrettyPrint(final boolean prettyPrint) {
		this.prettyPrint = prettyPrint;
	}

}
