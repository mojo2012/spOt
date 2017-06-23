package at.spot.core.infrastructure.service;

import java.util.Currency;
import java.util.Locale;
import java.util.Set;

import org.springframework.context.i18n.LocaleContextHolder;

public interface I18nService {

	/**
	 * Returns the default locale - configured in the application properties.
	 * This locale is also set as java default locale:
	 * {@link Locale#setDefault(Locale)}
	 */
	Locale getDefaultLocale();

	/**
	 * Returns the locale set as default in the current thread. See
	 * {@link LocaleContextHolder#getLocale()}.
	 * 
	 * @return
	 */
	Locale getCurrentLocale();

	/**
	 * Returns all available locales.
	 * 
	 * @return
	 */
	Set<Locale> getAllAvailableLocales();

	/**
	 * Returns the default currency - configured in the application properties.
	 * 
	 * @return
	 */
	Currency getDefaultCurrency();

	/**
	 * Returns the currently used currency, either defined by
	 * {@link #getDefaultCurrency()} or by the system.
	 * 
	 * @return
	 */
	Currency getCurrentCurrency();

	/**
	 * Returns all available currencies.
	 * 
	 * @return
	 */
	Set<Currency> getAllAvailableCurrencies();
}