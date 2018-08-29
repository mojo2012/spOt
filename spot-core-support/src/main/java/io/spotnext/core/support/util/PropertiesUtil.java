package io.spotnext.core.support.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>PropertiesUtil class.</p>
 *
 * @since 1.0
 */
public class PropertiesUtil {

	private static Logger LOG = LoggerFactory.getLogger(PropertiesUtil.class);

	/**
	 * Loads a {@link java.util.Properties} from a file.
	 *
	 * @param propertyFile
	 *            if it is a relative path "user.dir" will be used to resolve
	 *            it.
	 * @return null if file can't be found
	 */
	public static Properties loadPropertiesFromFile(final String propertyFile) {
		Path propPath = Paths.get(propertyFile);

		if (!propPath.isAbsolute()) {
			final Path currentDir = Paths.get(System.getProperty("user.dir"));
			propPath = currentDir.resolve(propPath);
		}

		return loadPropertiesFromFile(propPath.toFile());
	}

	/**
	 * Loads {@link java.util.Properties} from the classpath.
	 *
	 * @param classPathFileName a {@link java.lang.String} object.
	 * @return a {@link java.util.Properties} object.
	 */
	public static Properties loadPropertiesFromClasspath(final String classPathFileName) {
		final InputStream input = PropertiesUtil.class.getClassLoader().getResourceAsStream(classPathFileName);

		final Properties prop = new Properties();

		try {
			prop.load(input);
		} catch (final IOException e) {
			LOG.warn(e.getMessage());
		}

		return prop;
	}

	/**
	 * Loads {@link java.util.Properties} from a file.
	 *
	 * @param file a {@link java.io.File} object.
	 * @return null if file can't be found
	 */
	public static Properties loadPropertiesFromFile(final File file) {
		final Properties prop = new Properties();

		Reader reader = null;

		try {
			reader = new InputStreamReader(new FileInputStream(file), "UTF-8");
			prop.load(reader);
		} catch (final IOException e) {
			LOG.warn(e.getMessage());
		} finally {
			MiscUtil.closeQuietly(reader);
		}

		return prop;
	}
}
