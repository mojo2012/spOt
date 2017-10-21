package at.spot.core.infrastructure.support.init;

import javax.annotation.Priority;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import at.spot.core.infrastructure.exception.ModuleInitializationException;

@Configuration
@Priority(value = -1)
// needed to avoid some spring/hibernate problems
@EnableAutoConfiguration(exclude = { HibernateJpaAutoConfiguration.class })
public abstract class ModuleInit {

	boolean alreadyInitializied = false;

	/**
	 * This is a hook to customize the initialization process. It is called
	 * after {@link Bootstrap} has finished doing the basic initialization (load
	 * config properties and spring configuration).
	 */
	protected abstract void initialize() throws ModuleInitializationException;

	/**
	 * Called when the spring application context has been initialized.
	 * 
	 * @param event
	 * @throws ModuleInitializationException
	 */
	@EventListener
	protected void onApplicationEvent(final ApplicationReadyEvent event) throws ModuleInitializationException {
		if (!alreadyInitializied) {
			initialize();
			alreadyInitializied = true;
		}
	}
}
