package io.spotnext.spring.web;

import java.util.Set;

import javax.servlet.FilterRegistration;
import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.web.WebApplicationInitializer;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.filter.DelegatingFilterProxy;
import org.springframework.web.servlet.DispatcherServlet;

import io.spotnext.core.infrastructure.support.init.Bootstrap;
import io.spotnext.core.infrastructure.support.init.BootstrapOptions;
import io.spotnext.core.infrastructure.support.init.ModuleInit;
import io.spotnext.core.infrastructure.support.spring.Registry;
import io.spotnext.spring.web.session.WebSessionFilter;
import io.spotnext.spring.web.session.WebSessionListener;

/**
 * This interface extends the
 * {@link io.spotnext.core.infrastructure.support.init.ModuleInit} with some
 * more functionality with web container support.
 *
 * @author mojo2012
 * @version 1.0
 * @since 1.0
 */
public interface WebModuleInit extends ServletContextListener, WebApplicationInitializer, ServletContainerInitializer {

	/** {@inheritDoc} */
	@Override
	default void contextInitialized(final ServletContextEvent event) {
	}

	/** {@inheritDoc} */
	@Override
	default void contextDestroyed(final ServletContextEvent event) {
	}

	/*
	 * *************************************************************************
	 * Embedded jetty initialization
	 * *************************************************************************
	 */

	/*
	 * *************************************************************************
	 * Tomcat initialization
	 * *************************************************************************
	 */

	/**
	 * {@inheritDoc}
	 *
	 * This is the entry point when using an embedded jetty.
	 */
	@Override
	default void onStartup(final Set<Class<?>> params, final ServletContext servletContext) throws ServletException {
		onStartup(servletContext);
	}

	/**
	 * {@inheritDoc}
	 *
	 * This is the spring entry point when using an servlet container like tomcat.
	 */
	@Override
	default void onStartup(final ServletContext servletContext) throws ServletException {
		startup(servletContext);
	}

	/**
	 * The spot core initialization process starts here. After if is finished, the
	 * web module is initialized.
	 *
	 * @param servletContext a {@link javax.servlet.ServletContext} object.
	 */
	default void startup(final ServletContext servletContext) {
		bootSpotCore(getModuleInitClass(), getApplicationConfigProperties(), null);
		loadWebModule(servletContext);
	}

	/**
	 * Startup the spot core bootstrap mechanism. Registers
	 * {@link io.spotnext.core.infrastructure.support.init.ModuleInit} as the spot
	 * module init class. Also allows to inject app properties and spring
	 * configuration.
	 *
	 * @param initClass        a {@link java.lang.Class} object.
	 * @param appConfigFile    a {@link java.lang.String} object.
	 * @param springConfigFile a {@link java.lang.String} object.
	 * @param                  <T> a T object.
	 */
	default <T extends ModuleInit> void bootSpotCore(final Class<T> initClass, final String appConfigFile,
			final String springConfigFile) {

		final BootstrapOptions conf = new BootstrapOptions();

		if (initClass != null) {
			conf.setInitClass(initClass);
		}

		if (StringUtils.isNotEmpty(appConfigFile)) {
			conf.setAppConfigFile(appConfigFile);
		}

		if (StringUtils.isNotEmpty(springConfigFile)) {
			conf.setSpringConfigFile(springConfigFile);
		}

		Bootstrap.bootstrap(conf).run();
	}

	/**
	 * This sets up the listeners, filters and main servlet.
	 *
	 * @param servletContext a {@link javax.servlet.ServletContext} object.
	 */
	default void loadWebModule(final ServletContext servletContext) {
		final WebApplicationContext context = getApplicationContext(servletContext);

		setupListeners(servletContext, context);
		setupFilters(servletContext, context);
		setupServlets(servletContext, context);
	}

	/**
	 * Setup the servlets - most likely just spring's DispatcherServlet
	 *
	 * @param servletContext a {@link javax.servlet.ServletContext} object.
	 * @param context        a
	 *                       {@link org.springframework.web.context.WebApplicationContext}
	 *                       object.
	 */
	default void setupServlets(final ServletContext servletContext, final WebApplicationContext context) {
		final ServletRegistration.Dynamic dispatcher = servletContext.addServlet("dispatcherServlet",
				new DispatcherServlet(context));
		dispatcher.setLoadOnStartup(1);
		dispatcher.addMapping("/");
	}

	/**
	 * Set spring security filter mapping.
	 *
	 * @param servletContext a {@link javax.servlet.ServletContext} object.
	 * @param context        a
	 *                       {@link org.springframework.context.ApplicationContext}
	 *                       object.
	 */
	default void setupFilters(final ServletContext servletContext, final ApplicationContext context) {
		final FilterRegistration.Dynamic filter = servletContext.addFilter("springSecurityFilterChain",
				DelegatingFilterProxy.class);
		filter.addMappingForUrlPatterns(null, false, "/*");

		final FilterRegistration.Dynamic webSessionFilter = servletContext.addFilter("webSessionFilter",
				WebSessionFilter.class);
		webSessionFilter.addMappingForUrlPatterns(null, false, "/*");
	}

	/**
	 * Registers {@link javax.servlet.ServletContextListener}s. By default the
	 * {@link WebModuleInit} class is registered as listener too. Although the
	 * {@link #contextInitialized(ServletContextEvent)} and
	 * {@link #contextDestroyed(ServletContextEvent)} by default don't do anything.
	 *
	 * @param servletContext a {@link javax.servlet.ServletContext} object.
	 * @param context        a
	 *                       {@link org.springframework.web.context.WebApplicationContext}
	 *                       object.
	 */
	default void setupListeners(final ServletContext servletContext, final WebApplicationContext context) {
		servletContext.addListener(this);

		servletContext.addListener(new ContextLoaderListener(context));
		// register a session listener that connects the web session to the spot
		// session service
		servletContext.addListener(WebSessionListener.class);
	}

	/**
	 * Returns the spot base spring context, registered in
	 * {@link io.spotnext.core.infrastructure.support.spring.Registry#getApplicationContext()}.
	 *
	 * @return a {@link org.springframework.context.ApplicationContext} object.
	 */
	default ApplicationContext getParentSpringContext() {
		return Registry.getApplicationContext();
	}

	/**
	 * Returns the the
	 * {@link io.spotnext.core.infrastructure.support.init.ModuleInit} class for
	 * this application.
	 * 
	 * @param <T> the subtype of {@link ModuleInit}
	 * @return a {@link java.lang.Class} object.
	 */
	<T extends ModuleInit> Class<T> getModuleInitClass();

	/**
	 * Returns the web spring context.
	 *
	 * @param servletContext a {@link javax.servlet.ServletContext} object.
	 * @return a {@link org.springframework.web.context.WebApplicationContext}
	 *         object.
	 */
	WebApplicationContext getApplicationContext(final ServletContext servletContext);

	/**
	 * Returns the main properties file.
	 *
	 * @return a {@link java.lang.String} object.
	 */
	String getApplicationConfigProperties();

}
