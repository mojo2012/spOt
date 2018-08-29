package io.spotnext.commerce.service;

import java.util.List;
import java.util.Map;

import io.spotnext.itemtype.commerce.catalog.Product;

/**
 * Provides functionality
 */
public interface ProductSearchService {

	/**
	 * Searches for products containing the given string. How the search term in
	 * is interpreted and what product properties are searched depends on the
	 * underlying implementation.
	 *
	 * @param freeTextSearchTerm a {@link java.lang.String} object.
	 * @return a {@link java.util.List} object.
	 */
	List<Product> search(String freeTextSearchTerm);

	/**
	 * Searches for products with the given properties.
	 *
	 * @param freeTextSearchTerms a {@link java.util.Map} object.
	 * @return a {@link java.util.List} object.
	 */
	List<Product> search(Map<String, String> freeTextSearchTerms);
}
