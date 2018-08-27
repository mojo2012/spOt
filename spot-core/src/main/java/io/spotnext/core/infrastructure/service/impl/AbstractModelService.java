package io.spotnext.core.infrastructure.service.impl;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.annotation.Resource;
import javax.validation.ConstraintViolation;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import io.spotnext.core.infrastructure.event.ItemModificationEvent;
import io.spotnext.core.infrastructure.event.ItemModificationEvent.ModificationType;
import io.spotnext.core.infrastructure.exception.ItemInterceptorException;
import io.spotnext.core.infrastructure.exception.ModelCreationException;
import io.spotnext.core.infrastructure.exception.ModelNotFoundException;
import io.spotnext.core.infrastructure.exception.ModelSaveException;
import io.spotnext.core.infrastructure.exception.ModelValidationException;
import io.spotnext.core.infrastructure.interceptor.ItemCreateInterceptor;
import io.spotnext.core.infrastructure.interceptor.ItemLoadInterceptor;
import io.spotnext.core.infrastructure.interceptor.ItemPrepareInterceptor;
import io.spotnext.core.infrastructure.interceptor.ItemRemoveInterceptor;
import io.spotnext.core.infrastructure.interceptor.ItemValidateInterceptor;
import io.spotnext.core.infrastructure.service.EventService;
import io.spotnext.core.infrastructure.service.ModelService;
import io.spotnext.core.infrastructure.service.TypeService;
import io.spotnext.core.infrastructure.service.UserService;
import io.spotnext.core.infrastructure.service.ValidationService;
import io.spotnext.core.infrastructure.support.ItemInterceptorRegistry;
import io.spotnext.core.persistence.exception.ModelNotUniqueException;
import io.spotnext.core.persistence.service.PersistenceService;
import io.spotnext.core.support.util.ClassUtil;
import io.spotnext.core.types.Item;
import io.spotnext.core.types.Localizable;
import io.spotnext.itemtype.core.user.User;
import io.spotnext.itemtype.core.user.UserGroup;

@SuppressWarnings("unchecked")
@Service
public abstract class AbstractModelService extends AbstractService implements ModelService {

	@Resource
	protected TypeService typeService;

	@Resource
	protected UserService<User, UserGroup> userService;

	@Resource
	protected EventService eventService;

	@Resource
	protected PersistenceService persistenceService;

	@Resource
	protected ValidationService validationService;

	@Resource
	protected ItemInterceptorRegistry<ItemCreateInterceptor<Item>> itemCreateInterceptorRegistry;

	@Resource
	protected ItemInterceptorRegistry<ItemValidateInterceptor<Item>> itemValidateInterceptorRegistry;

	@Resource
	protected ItemInterceptorRegistry<ItemPrepareInterceptor<Item>> itemPrepareInterceptorRegistry;

	@Resource
	protected ItemInterceptorRegistry<ItemLoadInterceptor<Item>> itemLoadInterceptorRegistry;

	@Resource
	protected ItemInterceptorRegistry<ItemRemoveInterceptor<Item>> itemRemoveInterceptorRegistry;

	@Override
	public <T extends Item> T create(final Class<T> type) throws ModelCreationException {
		final String typeCode = typeService.getTypeCodeForClass(type);

		final T item = getApplicationContext().getBean(typeCode, type);
		persistenceService.initItem(item);

		for (final Class<?> superClass : ClassUtil.getAllSuperClasses(item.getClass(), Item.class, false, true)) {
			final String superTypeCode = typeService.getTypeCodeForClass((Class<Item>) superClass);
			final List<ItemCreateInterceptor<Item>> interceptors = itemCreateInterceptorRegistry
					.getValues(superTypeCode);

			if (CollectionUtils.isNotEmpty(interceptors)) {
				interceptors.stream().forEach(l -> l.onCreate(item));
			}
		}

		return item;
	}

	protected <T extends Item> void applyLoadInterceptors(final List<T> items) throws ItemInterceptorException {
		for (final T item : items) {
			for (final Class<?> superClass : ClassUtil.getAllSuperClasses(item.getClass(), Item.class, false, true)) {
				final String superTypeCode = typeService.getTypeCodeForClass((Class<Item>) superClass);
				final List<ItemLoadInterceptor<Item>> interceptors = itemLoadInterceptorRegistry
						.getValues(superTypeCode);

				if (CollectionUtils.isNotEmpty(interceptors)) {
					interceptors.stream().forEach(l -> l.onLoad(item));
				}
			}
		}
	}

	protected <T extends Item> void applyRemoveInterceptors(final List<T> items) throws ItemInterceptorException {
		for (final T item : items) {
			for (final Class<?> superClass : ClassUtil.getAllSuperClasses(item.getClass(), Item.class, false, true)) {
				final String superTypeCode = typeService.getTypeCodeForClass((Class<Item>) superClass);
				final List<ItemRemoveInterceptor<Item>> interceptors = itemRemoveInterceptorRegistry
						.getValues(superTypeCode);

				if (CollectionUtils.isNotEmpty(interceptors)) {
					interceptors.stream().forEach(l -> l.onRemove(item));
				}
			}
		}
	}

	protected <T extends Item> void applyPrepareInterceptors(final List<T> items)
			throws ModelSaveException, ModelNotUniqueException, ModelValidationException {

		for (final T item : items) {
			for (final Class<?> superClass : ClassUtil.getAllSuperClasses(item.getClass(), Item.class, false, true)) {
				final String superTypeCode = typeService.getTypeCodeForClass((Class<Item>) superClass);
				final List<ItemPrepareInterceptor<Item>> interceptors = itemPrepareInterceptorRegistry
						.getValues(superTypeCode);

				if (CollectionUtils.isNotEmpty(interceptors)) {
					try {
						interceptors.stream().forEach(l -> l.onPrepare(item));
					} catch (final ItemInterceptorException e) {
						throw new ModelSaveException("Error while applying prepare interceptors.", e);
					}
				}
			}
		}
	}

	protected <T extends Item> void validateModels(final List<T> items) throws ModelValidationException {
		for (final T item : items) {
			validateModel(item);
		}
	}

	protected <T extends Item> void validateModel(final T item) throws ModelValidationException {
		if (item == null) {
			throw new ModelValidationException("Given item is null");
		}

		final Set<ConstraintViolation<T>> errors = validationService.validate(item);

		for (final Class<?> superClass : ClassUtil.getAllSuperClasses(item.getClass(), Item.class, false, true)) {
			final String superTypeCode = typeService.getTypeCodeForClass((Class<Item>) superClass);
			final List<ItemValidateInterceptor<Item>> interceptors = itemValidateInterceptorRegistry
					.getValues(superTypeCode);

			if (CollectionUtils.isNotEmpty(interceptors)) {
				try {
					interceptors.stream().forEach(l -> l.onValidate(item));
				} catch (ModelValidationException e) {
//					errors.addAll((Collection<? extends ConstraintViolation<T>>) e.getConstraintViolations());
					throw e;
				}
			}
		}

		if (!errors.isEmpty()) {
			final String message = validationService.convertToReadableMessage(Collections.unmodifiableSet(errors));

			throw new ModelValidationException(message, errors);
		}
	}

	protected <T extends Item> void setTypeCode(final T item) {
		ClassUtil.setField(item, "typeCode", typeService.getTypeCodeForClass(item.getClass()));
	}

	@Override
	public <T extends Item> void detach(final T... items) {
		persistenceService.detach(Arrays.asList(items));
	}

	@Override
	public <T extends Item> void detach(final List<T> items) {
		detach(items.toArray(new Item[0]));
	}

	/**
	 * Sets the {@link Item#setCreatedBy(String)} and
	 * {@link Item#setLastModifiedBy(String)}.
	 */
	public <T extends Item> void setUserInformation(final List<T> models) {
		final User currentUser = userService.getCurrentUser();

		if (currentUser == null) {
			loggingService.debug(() -> "Could not determine current session user");
		}

		final String username = currentUser != null ? currentUser.getId() : "<system>";

		for (final T model : models) {
			ClassUtil.setField(model, "createdBy", username);
			ClassUtil.setField(model, "lastModifiedBy", username);
		}
	}

	/**
	 * Asynchronously publishes item modification events.
	 * 
	 * @param items
	 *            the items that have been modified
	 * @param modificationType
	 *            the kind of modification that has been applied
	 */
	protected <T extends Item> void publishEvents(final List<T> items, final ModificationType modificationType) {
		for (final T item : items) {
			if (item != null) {
				try {
					eventService.multicastEvent(new ItemModificationEvent<>(item, modificationType));
				} catch (final Exception e) {
					loggingService.exception("Could not publish item modification event.", e);
				}
			} else {
				loggingService.warn("Cannot publish item modification event for <null>");
			}
		}
	}

	@Override
	public <T extends Item> Object getPropertyValue(final T item, final String propertyName) {
		return getPropertyValue(item, propertyName, Item.class);
	}

	@Override
	public <T extends Item, V> V getLocalizedPropertyValue(final T item, final String propertyName,
			final Class<V> valueType, final Locale locale) {

		final Localizable<V> localizable = (Localizable<V>) ClassUtil.getField(item, propertyName, true);

		if (localizable != null) {
			return localizable.get(locale);
		} else {
			loggingService.debug(String.format("Localized property %s on type %s not found", propertyName,
					item.getClass().getSimpleName()));
		}

		return null;
	}

	@Override
	public <T extends Item, V> V getPropertyValue(final T item, final String propertyName, final Class<V> valueType) {
		return (V) ClassUtil.getField(item, propertyName, true);
	}

	@Override
	public <T extends Item> void setLocalizedPropertyValue(final T item, final String propertyName,
			final Object propertyValue, final Locale locale) {

		final Localizable<Object> localizable = (Localizable<Object>) ClassUtil.getField(item, propertyName, true);

		if (localizable != null) {
			localizable.set(locale, propertyValue);
		} else {
			loggingService.debug(String.format("Localized property %s on type %s not found", propertyName,
					item.getClass().getSimpleName()));
		}
	}

	@Override
	public <T extends Item> void setPropertyValue(final T item, final String propertyName, final Object propertyValue) {
		ClassUtil.setField(item, propertyName, propertyValue);
	}

	@Override
	public <T extends Item> boolean isAttached(final T item) {
		return persistenceService.isAttached(item);
	}

	@Override
	public <T extends Item> void attach(final T item) throws ModelNotFoundException {
		persistenceService.attach(item);
	}
}
