package io.spotnext.instrumentation;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

import javax.annotation.concurrent.ThreadSafe;

import org.apache.commons.io.IOUtils;
import org.springframework.context.support.GenericXmlApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.instrument.classloading.InstrumentationLoadTimeWeaver;

import io.spotnext.instrumentation.internal.AgentClassLoaderReference;
import io.spotnext.instrumentation.internal.DynamicInstrumentationAgent;
import io.spotnext.instrumentation.internal.DynamicInstrumentationLoadAgentMain;
import io.spotnext.instrumentation.internal.JdkFilesFinder;
import io.spotnext.instrumentation.util.Assert;

/**
 * This class installs dynamic instrumentation into the current JVM.
 *
 * @since 1.0
 */
@ThreadSafe
public final class DynamicInstrumentationLoader {

	private static final String KEY_TRANSFORMERS = "transformers";
	private static final String LOAD_AGENT_THREAD_NAME = "instrumentationAgentStarter";

	private static volatile Throwable threadFailed;
	private static volatile String toolsJarPath;
	private static volatile String attachLibPath;

	private static Class<? extends ClassFileTransformer>[] registeredTranformers;

	/**
	 * Keeping a reference here so it is not garbage collected
	 */
	static GenericXmlApplicationContext ltwCtx;

	/**
	 * <p>
	 * Constructor for DynamicInstrumentationLoader.
	 * </p>
	 */
	protected DynamicInstrumentationLoader() {
	}

	public static List<Class<? extends ClassFileTransformer>> getRegisteredTranformers() {
		return Arrays.asList(registeredTranformers);
	}

	/**
	 * <p>
	 * initialize.
	 * </p>
	 *
	 * @param transformers a {@link java.lang.Class} object.
	 */
	@SafeVarargs
	public static void initialize(final Class<? extends ClassFileTransformer>... transformers) {
		registeredTranformers = transformers;

		try {
			while (!isInstrumentationAvailable() && threadFailed == null) {
				TimeUnit.MILLISECONDS.sleep(1);
			}
			if (threadFailed != null) {
				final String javaVersion = getJavaVersion();
				final String javaHome = getJavaHome();
				throw new RuntimeException("Additional information: javaVersion=" + javaVersion + "; javaHome="
						+ javaHome + "; toolsJarPath=" + toolsJarPath + "; attachLibPath=" + attachLibPath,
						threadFailed);
			}
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * Checks if the instrumentation is enabled.
	 *
	 * @return a boolean.
	 */
	public static boolean isInstrumentationAvailable() {
		return InstrumentationLoadTimeWeaver.isInstrumentationAvailable();
	}

	/**
	 * Creates a generic spring context with enabled load time weaving.
	 *
	 * @return a {@link org.springframework.context.support.GenericXmlApplicationContext} object.
	 */
	public static synchronized GenericXmlApplicationContext initLoadTimeWeavingSpringContext() {
		Assert.assertTrue(isInstrumentationAvailable(), "Instrumentation not available");

		if (ltwCtx == null) {
			final GenericXmlApplicationContext ctx = new GenericXmlApplicationContext();
			ctx.load(new ClassPathResource("/META-INF/ctx.spring.weaving.xml"));
			ctx.refresh();
			ltwCtx = ctx;
		}

		return ltwCtx;
	}

	static {
		if (!isInstrumentationAvailable()) {
			try {
				final File tempAgentJar = createTempAgentJar();
				setAgentClassLoaderReference();
				final String pid = DynamicInstrumentationProperties.getProcessId();
				final Thread loadAgentThread = new Thread(LOAD_AGENT_THREAD_NAME) {

					@Override
					public void run() {
						try {
							loadAgent(tempAgentJar, pid);
						} catch (final Throwable e) {
							threadFailed = e;
							throw new RuntimeException(e);
						}
					}
				};

				DynamicInstrumentationReflections.addPathToSystemClassLoader(tempAgentJar);

				final JdkFilesFinder jdkFilesFinder = new JdkFilesFinder();

				if (DynamicInstrumentationReflections.isBeforeJava9()) {
					final File toolsJar = jdkFilesFinder.findToolsJar();
					DynamicInstrumentationReflections.addPathToSystemClassLoader(toolsJar);
					DynamicInstrumentationLoader.toolsJarPath = toolsJar.getAbsolutePath();

					final File attachLib = jdkFilesFinder.findAttachLib();
					DynamicInstrumentationReflections.addPathToJavaLibraryPath(attachLib.getParentFile());
					DynamicInstrumentationLoader.attachLibPath = attachLib.getAbsolutePath();
				}

				loadAgentThread.start();
			} catch (final Exception e) {
				throw new RuntimeException(e);
			}

		}
	}

	/**
	 * <p>
	 * loadAgent.
	 * </p>
	 *
	 * @param tempAgentJar a {@link java.io.File} object.
	 * @param pid          a {@link java.lang.String} object.
	 * @throws java.lang.Exception if any.
	 */
	protected static void loadAgent(final File tempAgentJar, final String pid) throws Exception {
		// transform transformer classes to comma-separated FQNs
		String registeredTransformers = DynamicInstrumentationLoader.getRegisteredTranformers().stream().map(t -> t.getName()).collect(Collectors.joining(","));

		if (DynamicInstrumentationReflections.isBeforeJava9()) {
			System.setProperty(KEY_TRANSFORMERS, registeredTransformers);
			DynamicInstrumentationLoadAgentMain.loadAgent(pid, tempAgentJar.getAbsolutePath());
		} else {
			// -Djdk.attach.allowAttachSelf
			// https://www.bountysource.com/issues/45231289-self-attach-fails-on-jdk9
			// workaround this limitation by attaching from a new process
			final File loadAgentJar = createTempJar(DynamicInstrumentationLoadAgentMain.class, false,
					io.spotnext.instrumentation.internal.DummyAttachProvider.class);
			final String javaExecutable = getJavaHome() + File.separator + "bin" + File.separator + "java";
			final List<String> command = new ArrayList<String>();
			command.add(javaExecutable);
			command.add("-classpath");
			command.add(loadAgentJar.getAbsolutePath()); // tools.jar not needed since java9
			command.add(DynamicInstrumentationLoadAgentMain.class.getName());
			command.add(pid);
			command.add(tempAgentJar.getAbsolutePath());
			command.add("-D" + KEY_TRANSFORMERS + "=" + registeredTransformers);

			Runtime.getRuntime().exec(command.toArray(new String[command.size()]));
		}
	}

	/**
	 * <p>
	 * getJavaHome.
	 * </p>
	 *
	 * @return a {@link java.lang.String} object.
	 */
	protected static String getJavaHome() {
		// CHECKSTYLE:OFF
		return System.getProperty("java.home");
		// CHECKSTYLE:ON
	}

	/**
	 * <p>
	 * getJavaVersion.
	 * </p>
	 *
	 * @return a {@link java.lang.String} object.
	 */
	protected static String getJavaVersion() {
		// CHECKSTYLE:OFF
		return System.getProperty("java.version");
		// CHECKSTYLE:ON
	}

	/**
	 * <p>
	 * setAgentClassLoaderReference.
	 * </p>
	 *
	 * @throws java.lang.Exception if any.
	 */
	protected static void setAgentClassLoaderReference() throws Exception {
		final Class<AgentClassLoaderReference> agentClassLoaderReferenceClass = AgentClassLoaderReference.class;
		final File tempAgentClassLoaderJar = createTempJar(agentClassLoaderReferenceClass, false);
		DynamicInstrumentationReflections.addPathToSystemClassLoader(tempAgentClassLoaderJar);
		final ClassLoader systemClassLoader = DynamicInstrumentationReflections.getSystemClassLoader();
		final Class<?> systemAgentClassLoaderReferenceClass = systemClassLoader
				.loadClass(agentClassLoaderReferenceClass.getName());
		final Method setAgentClassLoaderMethod = systemAgentClassLoaderReferenceClass
				.getDeclaredMethod("setAgentClassLoader", ClassLoader.class);
		setAgentClassLoaderMethod.invoke(null, DynamicInstrumentationReflections.getContextClassLoader());
	}

	/**
	 * <p>
	 * createTempAgentJar.
	 * </p>
	 *
	 * @return a {@link java.io.File} object.
	 * @throws java.lang.ClassNotFoundException if any.
	 */
	protected static File createTempAgentJar() throws ClassNotFoundException {
		try {
			return createTempJar(DynamicInstrumentationAgent.class, true);
		} catch (final Throwable e) {
			final String message = "Unable to find class [" + DynamicInstrumentationAgent.class.getName() + "] in classpath."
					+ "\nPlease make sure you have added spot-instrumentation.jar to your classpath properly,"
					+ "\nor make sure you have embedded it correctly into your fat-jar."
					+ "\nThey can be created e.g. with \"maven-shade-plugin\"."
					+ "\nPlease be aware that some fat-jar solutions might not work well due to classloader issues.";
			throw new ClassNotFoundException(message, e);
		}
	}

	/**
	 * Creates a new jar that only contains the {@link DynamicInstrumentationAgent} class.
	 *
	 * @param clazz a {@link java.lang.Class} object.
	 * @param agent a boolean.
	 * @return a {@link java.io.File} object.
	 * @throws java.lang.Exception if any.
	 */
	protected static File createTempJar(final Class<?> clazz, final boolean agent, final Class<?>... additionalClasses) throws Exception {
		final String className = clazz.getName();
		final File tempAgentJar = new File(DynamicInstrumentationProperties.TEMP_DIRECTORY, className + ".jar");
		final Manifest manifest = new Manifest(clazz.getResourceAsStream("/META-INF/MANIFEST.MF"));
		if (agent) {
			manifest.getMainAttributes().putValue("Premain-Class", className);
			manifest.getMainAttributes().putValue("Agent-Class", className);
			manifest.getMainAttributes().putValue("Can-Redefine-Classes", String.valueOf(true));
			manifest.getMainAttributes().putValue("Can-Retransform-Classes", String.valueOf(true));
			manifest.getMainAttributes().putValue("Permissions", String.valueOf("all-permissions"));
		}
		final JarOutputStream tempJarOut = new JarOutputStream(new FileOutputStream(tempAgentJar), manifest);
		final JarEntry entry = new JarEntry(className.replace(".", "/") + ".class");
		tempJarOut.putNextEntry(entry);
		final InputStream classIn = DynamicInstrumentationReflections.getClassInputStream(clazz);
		IOUtils.copy(classIn, tempJarOut);
		tempJarOut.closeEntry();

		if (additionalClasses != null) {
			for (final Class<?> additionalClazz : additionalClasses) {
				final String additionalClassName = additionalClazz.getName();
				final JarEntry additionalEntry = new JarEntry(additionalClassName.replace(".", "/") + ".class");
				tempJarOut.putNextEntry(additionalEntry);
				final InputStream additionalClassIn = DynamicInstrumentationReflections
						.getClassInputStream(additionalClazz);
				IOUtils.copy(additionalClassIn, tempJarOut);
				tempJarOut.closeEntry();
			}
		}

		tempJarOut.close();
		return tempAgentJar;
	}

}
