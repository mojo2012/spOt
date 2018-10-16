package io.spotnext.core.infrastructure.aspect;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;

import io.spotnext.core.infrastructure.service.ModelService;
import io.spotnext.core.infrastructure.support.Logger;
import io.spotnext.core.persistence.service.QueryService;
import io.spotnext.core.persistence.valueprovider.ItemPropertyValueProvider;
import io.spotnext.infrastructure.annotation.Accessor;
import io.spotnext.infrastructure.annotation.Property;
import io.spotnext.infrastructure.annotation.Relation;
import io.spotnext.infrastructure.type.Item;
import io.spotnext.support.util.ClassUtil;

//@Aspect
/**
 * <p>ItemPropertyAccessAspect class.</p>
 *
 * @author mojo2012
 * @version 1.0
 * @since 1.0
 */
public class ItemPropertyAccessAspect extends AbstractBaseAspect {

	@Resource
	protected ModelService modelService;

	@Resource
	protected QueryService queryService;

	/**
	 * <p>init.</p>
	 */
	@PostConstruct
	public void init() {
		Logger.debug("Initialized item property access aspect.");
	}

	// @Autowired
	Map<String, ItemPropertyValueProvider> itemPropertyValueProviders = new HashMap<>();

	/*
	 * PointCuts
	 */

	// @Pointcut("!within(io.spotnext.core.persistence..*) &&
	// !within(io.spotnext.infrastructure.aspect..*)")

	protected void notFromPersistencePackage() {
	};

	/**
	 * Define the pointcut for all fields that are accessed (get) on an object of
	 * type @Item that are annotated with @Property.
	 */
	// @Pointcut("@annotation(io.spotnext.infrastructure.annotation.Property) &&
	// get(* *.*)")

	// @Pointcut("@annotation(io.spotnext.infrastructure.annotation.Property) &&
	// get(* *.*)")
	final protected void getField() {
	};

	/**
	 * Define the pointcut for all getter that are accessing a field in an object of
	 * type @Item that are annotated with @Property.
	 */
	// @Pointcut("@annotation(io.spotnext.infrastructure.annotation.GetProperty)
	// && within(@io.spotnext.infrastructure.annotation.ItemType *)")
	//
	// final protected void getMethod() {
	// };

	/**
	 * Define the pointcut for all fields that are accessed (set) on an object of
	 * type @Item that are annotated with @Property.
	 */
	// @Pointcut("@annotation(io.spotnext.infrastructure.annotation.Property) &&
	// set(* *.*)")

	// @Pointcut("@annotation(io.spotnext.infrastructure.annotation.Property) &&
	// set(* *.*)")
	final protected void setField() {
	};

	/**
	 * Define the pointcut for all fields that are accessed (set) on an object of
	 * type @Item that are annotated with @Property.
	 *
	 * @param joinPoint a {@link org.aspectj.lang.JoinPoint} object.
	 */
	// @Pointcut("@annotation(io.spotnext.infrastructure.annotation.SetProperty)
	// && "
	// + "within(@io.spotnext.infrastructure.annotation.ItemType *) &&
	// execution(* set*(..))")
	//
	// final protected void setMethod() {
	// };

	/*
	 * JoinPoints
	 */

	// @After("setField() && notFromPersistencePackage()")
	public void setPropertyValue(final JoinPoint joinPoint) {
		final Property ann = getAnnotation(joinPoint, Property.class);
		final Accessor setAnn = getAnnotation(joinPoint, Accessor.class);

		if (setAnn == null && (ann == null || !ann.writable())) {
			throw new RuntimeException(String.format("Attribute %s is not writable.", createSignature(joinPoint)));
		}

		// handle relation annotation
		final Relation rel = getAnnotation(joinPoint, Relation.class);

		if (rel != null) {
			Logger.warn("Handling relations not implemented here.");
			// handleRelationProperty(joinPoint, rel);
		}

		// set the changed field to dirty
		if (joinPoint.getTarget() instanceof Item) {
			ClassUtil.invokeMethod(joinPoint.getTarget(), "markAsDirty", joinPoint.getSignature().getName());
		}
	}

	// @Around("getField() && notFromPersistencePackage()")
	/**
	 * <p>getPropertyValue.</p>
	 *
	 * @param joinPoint a {@link org.aspectj.lang.ProceedingJoinPoint} object.
	 * @return a {@link java.lang.Object} object.
	 * @throws java.lang.Throwable if any.
	 */
	public Object getPropertyValue(final ProceedingJoinPoint joinPoint) throws Throwable {
		final Property ann = getAnnotation(joinPoint, Property.class);
		final Accessor getAnn = getAnnotation(joinPoint, Accessor.class);

		if (getAnn == null && (ann == null || !ann.readable())) {
			throw new RuntimeException(String.format("Attribute %s is not readable.", createSignature(joinPoint)));
		}

		// if the target is a proxy item, we load it first, then we invoke the
		// getter functionality
		if (joinPoint.getTarget() instanceof Item) {
			final Item i = (Item) joinPoint.getTarget();

			if (i.isPersisted()) {
				modelService.refresh(i);
			}
		}

		// if there's a value provider configured, use it
		if (ann != null && StringUtils.isNotBlank(ann.itemValueProvider())) {
			final ItemPropertyValueProvider pv = itemPropertyValueProviders.get(ann.itemValueProvider());
			return pv.readValue((Item) joinPoint.getTarget(), joinPoint.getSignature().getName());
		} else { // get currently stored object
			final Object retVal = getPropertyValueInternal(joinPoint);
			return retVal;
		}
	}

	protected Object getPropertyValueInternal(final ProceedingJoinPoint joinPoint) throws Throwable {
		return joinPoint.proceed(joinPoint.getArgs());
	}

	/**
	 * <p>Getter for the field <code>modelService</code>.</p>
	 *
	 * @return a {@link io.spotnext.infrastructure.service.ModelService} object.
	 */
	public ModelService getModelService() {
		return modelService;
	}

	/**
	 * <p>Setter for the field <code>modelService</code>.</p>
	 *
	 * @param modelService a {@link io.spotnext.infrastructure.service.ModelService} object.
	 */
	public void setModelService(final ModelService modelService) {
		this.modelService = modelService;
	}

	/**
	 * <p>Getter for the field <code>queryService</code>.</p>
	 *
	 * @return a {@link io.spotnext.core.persistence.service.QueryService} object.
	 */
	public QueryService getQueryService() {
		return queryService;
	}

	/**
	 * <p>Setter for the field <code>queryService</code>.</p>
	 *
	 * @param queryService a {@link io.spotnext.core.persistence.service.QueryService} object.
	 */
	public void setQueryService(final QueryService queryService) {
		this.queryService = queryService;
	}

}
