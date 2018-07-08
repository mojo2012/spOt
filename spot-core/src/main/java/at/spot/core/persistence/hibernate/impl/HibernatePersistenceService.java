package at.spot.core.persistence.hibernate.impl;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.persistence.EntityGraph;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityNotFoundException;
import javax.persistence.PersistenceException;
import javax.persistence.PersistenceUnit;
import javax.persistence.Subgraph;
import javax.persistence.TransactionRequiredException;
import javax.persistence.Tuple;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.hibernate.HibernateException;
import org.hibernate.LockMode;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.jpa.QueryHints;
import org.hibernate.query.Query;
import org.hibernate.tool.hbm2ddl.SchemaExport;
import org.hibernate.tool.hbm2ddl.SchemaUpdate;
import org.hibernate.tool.schema.TargetType;
import org.hibernate.tool.schema.internal.ExceptionHandlerLoggedImpl;
import org.hibernate.tool.schema.spi.SchemaManagementException;
import org.hibernate.tool.schema.spi.SchemaManagementTool;
import org.hibernate.tool.schema.spi.SchemaManagementToolCoordinator;
import org.hibernate.tool.schema.spi.SchemaValidator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import at.spot.core.persistence.query.ModelQuery;

import at.spot.core.infrastructure.annotation.Property;
import at.spot.core.infrastructure.exception.ModelNotFoundException;
import at.spot.core.infrastructure.exception.ModelSaveException;
import at.spot.core.infrastructure.exception.UnknownTypeException;
import at.spot.core.infrastructure.support.ItemTypePropertyDefinition;
import at.spot.core.model.Item;
import at.spot.core.persistence.exception.ModelNotUniqueException;
import at.spot.core.persistence.exception.QueryException;
import at.spot.core.persistence.service.TransactionService;
import at.spot.core.persistence.service.impl.AbstractPersistenceService;
import at.spot.core.support.util.ClassUtil;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressFBWarnings("BC_UNCONFIRMED_CAST_OF_RETURN_VALUE")
public class HibernatePersistenceService extends AbstractPersistenceService {

	static final int JDBC_BATCH_SIZE = 20;

	protected MetadataExtractorIntegrator metadataIntegrator = MetadataExtractorIntegrator.INSTANCE;

	@PersistenceUnit
	protected EntityManagerFactory entityManagerFactory;

	@Resource
	protected TransactionService transactionService;

	@Resource
	protected PlatformTransactionManager transactionManager;

	@PostConstruct
	public void initialize() {
		if (configurationService.getBoolean("initializetypesystem", false)) {
			loggingService.info("Initializing type system schema ...");

			final SchemaExport schemaExport = new SchemaExport();
			schemaExport.setHaltOnError(true);
			schemaExport.setFormat(true);
			schemaExport.setDelimiter(";");
			schemaExport.setOutputFile("db-schema.sql");

			try {
				schemaExport.drop(EnumSet.of(TargetType.DATABASE, TargetType.STDOUT), metadataIntegrator.getMetadata());
			} catch (Exception e) {
				loggingService.warn("Could not drop type system schema.");
			}

			schemaExport.createOnly(EnumSet.of(TargetType.DATABASE, TargetType.STDOUT),
					metadataIntegrator.getMetadata());
		}

		if (configurationService.getBoolean("updatetypesystem", false)) {
			loggingService.info("Updating type system schema ...");

			final SchemaUpdate schemaExport = new SchemaUpdate();
			schemaExport.setHaltOnError(true);
			schemaExport.setFormat(true);
			schemaExport.setDelimiter(";");
			schemaExport.setOutputFile("db-schema.sql");
			schemaExport.execute(EnumSet.of(TargetType.DATABASE, TargetType.STDOUT), metadataIntegrator.getMetadata());
		}

		// validate schema
		final SchemaManagementTool tool = metadataIntegrator.getServiceRegistry()
				.getService(SchemaManagementTool.class);

		try {
			SchemaValidator validator = tool.getSchemaValidator(entityManagerFactory.getProperties());
			validator.doValidation(metadataIntegrator.getMetadata(), SchemaManagementToolCoordinator
					.buildExecutionOptions(entityManagerFactory.getProperties(), ExceptionHandlerLoggedImpl.INSTANCE));

			loggingService.debug("Type system schema seems to be OK");

		} catch (SchemaManagementException e) {
			loggingService.warn("Type system schema needs to be initialized/updated");
		}

		if (configurationService.getBoolean("cleantypesystem", false)) {
			loggingService.info("Cleaning type system ... (not yet implemented)");
		}
	}

	@SuppressFBWarnings("REC_CATCH_EXCEPTION")
	@Override
	public <T> List<T> query(final at.spot.core.persistence.query.JpqlQuery<T> sourceQuery) throws QueryException {

		List<T> results;

		try {
			final Session session = getSession();

			// if this is an item type, we just load the entities
			// if it is a "primitive" natively supported type we can also just
			// let hibernate do the work
			if (Item.class.isAssignableFrom(sourceQuery.getResultClass())
					|| NATIVE_DATATYPES.contains(sourceQuery.getResultClass())) {

				final Query<T> query = session.createQuery(sourceQuery.getQuery(), sourceQuery.getResultClass())
						.setReadOnly(true).setHint(QueryHints.HINT_CACHEABLE, true);

				setFetchSubGraphsHint(session, sourceQuery, query);
				setParameters(sourceQuery.getParams(), query);
				setPage(query, sourceQuery.getPage());
				setPageSize(query, sourceQuery.getPageSize());
				results = query.getResultList();

			} else {
				// otherwise we load each value into a list of tuples
				// in that case the selected columns need to be aliased in case
				// the given result
				// type has no constructor that exactly matches the returned
				// columns' types, as
				// otherwise we cannot map the row values to properties.

				final Query<Tuple> query = session.createQuery(sourceQuery.getQuery(), Tuple.class).setReadOnly(true)
						.setHint(QueryHints.HINT_CACHEABLE, true);

				setParameters(sourceQuery.getParams(), query);
				setPage(query, sourceQuery.getPage());
				setPageSize(query, sourceQuery.getPageSize());

				final List<Tuple> resultList = query.list();
				results = new ArrayList<>();

				for (final Tuple t : resultList) {
					final List<Class<?>> tupleElements = t.getElements().stream().map(e -> e.getJavaType())
							.collect(Collectors.toList());

					// first try to create the pojo using a constructor that
					// matches the result's
					// column types

					final List<Object> values = t.getElements().stream().map(e -> t.get(e))
							.collect(Collectors.toList());
					Optional<T> pojo = ClassUtil.instantiate(sourceQuery.getResultClass(), values.toArray());

					// if the pojo can't be instantated, we try to create it
					// manually and inject the
					// data using reflection
					// for this to work, each selected column has to have the
					// same alias as the
					// pojo's property!
					if (!pojo.isPresent()) {
						final Optional<T> obj = ClassUtil.instantiate(sourceQuery.getResultClass());

						if (obj.isPresent()) {
							final Object o = obj.get();
							t.getElements().stream()
									.forEach(el -> ClassUtil.setField(o, el.getAlias(), t.get(el.getAlias())));
						}

						pojo = obj;
					}

					if (pojo.isPresent()) {
						results.add(pojo.get());
					} else {
						throw new InstantiationException(
								String.format("Could not instantiate result type '%s'", sourceQuery.getResultClass()));
					}
				}
			}
		} catch (QueryException e) {
			throw e;
		} catch (final Exception e) {
			throw new QueryException(String.format("Could not execute query '%s'", sourceQuery.getQuery()), e);
		}

		return results;
	}

	protected <T, Q extends at.spot.core.persistence.query.Query<T>> void setFetchSubGraphsHint(final Session session,
			final Q sourceQuery, final TypedQuery<T> query) throws UnknownTypeException {

		if (!Item.class.isAssignableFrom(sourceQuery.getResultClass())) {
			loggingService.warn("Fetch sub graphs can only be used for item queries.");
			return;
		}

		final List<String> fetchSubGraphs = new ArrayList<>();

		if (sourceQuery.isEagerFetchRelations()) {
			final Map<String, ItemTypePropertyDefinition> props = typeService
					.getItemTypeProperties(typeService.getTypeCodeForClass((Class<Item>) sourceQuery.getResultClass()));

			// add all properties
			final List<String> validProperties = props.values().stream() //
					.filter(p -> Item.class.isAssignableFrom(p.getReturnType()) || p.getRelationDefinition() != null) //
					.map(p -> p.getName()) //
					.collect(Collectors.toList());
			fetchSubGraphs.addAll(validProperties);
		} else if (sourceQuery.getEagerFetchRelationProperties().size() > 0) {
			fetchSubGraphs.addAll(sourceQuery.getEagerFetchRelationProperties());
		}

		if (fetchSubGraphs.size() > 0) {
			final EntityGraph<T> graph = session.createEntityGraph(sourceQuery.getResultClass());

			for (final String subgraph : fetchSubGraphs) {
				final Subgraph itemGraph = graph.addSubgraph(subgraph);
			}

			query.setHint("javax.persistence.loadgraph", graph);
		}
	}

	protected <T> void setParameters(final Map<String, Object> params, final Query<T> query) {
		for (final Map.Entry<String, Object> entry : params.entrySet()) {
			query.setParameter(entry.getKey(), entry.getValue());
		}
	}

	protected void setPage(final Query<?> query, final int page) {
		if (page >= 0) {
			query.setFirstResult(page);
		}
	}

	protected void setPageSize(final Query<?> query, final int pageSize) {
		if (pageSize >= 0) {
			query.setFetchSize(pageSize);
		}
	}

	@Override
	public <T extends Item> void save(final List<T> items) throws ModelSaveException, ModelNotUniqueException {
		bindSession();

		try {
			transactionService.execute(() -> {
				final Session session = getSession();
				int i = 0;

				for (final T item : items) {
					try {
						session.saveOrUpdate(item);
						// getSession().merge(item);

						if (i % JDBC_BATCH_SIZE == 0) { // use same as the JDBC
														// batch size
							// flush a batch of inserts and release memory:
							session.flush();
							session.clear();
						}
						i++;
					} catch (final DataIntegrityViolationException | TransactionRequiredException
							| IllegalArgumentException e) {

						throw new ModelSaveException("Could not save given items: " + e.getMessage(), e);

					} catch (final PersistenceException e) {
						final Throwable rootCause = ExceptionUtils.getRootCause(e);
						final String rootCauseMessage = rootCause != null ? rootCause.getMessage() : e.getMessage();

						throw new ModelSaveException(rootCauseMessage, e);
					}
				}

				try {
					refresh(items);
				} catch (final ModelNotFoundException e) {
					throw new ModelSaveException("Could not save given items", e);
				}

				return null;
			});
		} catch (final TransactionException e) {
			if (e.getCause() instanceof ModelSaveException) {
				throw (ModelSaveException) e.getCause();
			} else if (e.getCause() instanceof ModelNotUniqueException) {
				throw (ModelNotUniqueException) e.getCause();
			} else {
				throw e;
			}
		}
	}

	@Override
	public <T extends Item> T load(final Class<T> type, final long pk) throws ModelNotFoundException {
		bindSession();

		try {
			return transactionService.execute(() -> {
				return getSession().find(type, pk);
			});
		} catch (final TransactionException e) {
			if (e.getCause() instanceof ModelNotFoundException) {
				throw (ModelNotFoundException) e.getCause();
			} else {
				throw e;
			}
		}
	}

	@Override
	public <T extends Item> void refresh(final List<T> items) throws ModelNotFoundException {
		bindSession();

		try {
			transactionService.execute(() -> {
				for (final T item : items) {
					try {
						attach(item);

						getSession().refresh(item);
					} catch (HibernateException | TransactionRequiredException | IllegalArgumentException
							| EntityNotFoundException e) {
						throw new ModelNotFoundException(
								String.format("Could not refresh item with pk=%s.", item.getPk()), e);
					}
				}

				return null;
			});
		} catch (final TransactionException e) {
			if (e.getCause() instanceof ModelNotFoundException) {
				throw (ModelNotFoundException) e.getCause();
			} else {
				throw e;
			}
		}
	}

	/**
	 * Attaches the given item in case it is detached.
	 * 
	 * @param item
	 * @throws ModelNotFoundException
	 */
	protected <T extends Item> void attach(final T item) throws ModelNotFoundException {
		bindSession();

		try {
			// ignore unpersisted or already attached items
			if (item.getPk() == null || getSession().contains(item)) {
				return;
			}

			final T attached = (T) getSession().load(item.getClass(), item.getPk());
			if (attached != null) {
				getSession().evict(attached);
				getSession().lock(item, LockMode.NONE);
			}
		} catch (HibernateException | TransactionRequiredException | IllegalArgumentException
				| EntityNotFoundException e) {
			throw new ModelNotFoundException(String.format("Could not refresh item with pk=%s.", item.getPk()), e);
		}
	}

	@Override
	public <T extends Item> List<T> load(final ModelQuery<T> sourceQuery) {

		bindSession();

		return transactionService.execute(() -> {

			TypedQuery<T> query = null;
			final Session session = getSession();
			final CriteriaBuilder cb = session.getCriteriaBuilder();

			final CriteriaQuery<T> cq = cb.createQuery(sourceQuery.getResultClass());
			final Root<T> r = cq.from(sourceQuery.getResultClass());

			if (sourceQuery.getSearchParameters() != null) {
				Predicate p = cb.conjunction();

				for (final Map.Entry<String, Object> entry : sourceQuery.getSearchParameters().entrySet()) {
					if (entry.getValue() instanceof Item && !((Item) entry.getValue()).isPersisted()) {
						throw new PersistenceException(String.format(
								"Passing non-persisted item as search param '%s' is not supported.", entry.getKey()));
					}

					p = cb.and(p, cb.equal(r.get(entry.getKey()), entry.getValue()));
				}

				final CriteriaQuery<T> select = cq.select(r).where(p);
				query = session.createQuery(select);

				if (sourceQuery.getPageSize() > 0) {
					query.setFirstResult((sourceQuery.getPage() - 1) * sourceQuery.getPageSize());
					query.setMaxResults(sourceQuery.getPageSize());
				}
			} else {
				query = session.createQuery(cq.select(r));
			}

			setFetchSubGraphsHint(session, sourceQuery, query);

			return ((Query<T>) query).getResultList();
		});
	}

	@Override
	public <T extends Item> void remove(final List<T> items) {
		bindSession();

		transactionService.execute(() -> {
			for (final T item : items) {
				getSession().remove(item);
			}
			return null;
		});
	}

	@Override
	public <T extends Item> void remove(final Class<T> type, final long pk) {
		bindSession();

		transactionService.execute(() -> {
			// TODO: improve
			// final String query = String.format("DELETE FROM %s WHERE pk IN
			// (?pk)",
			// type.getSimpleName());

			// em.createQuery(query, type).setParameter("pk", pk);
			final T item = getSession().find(type, pk);
			getSession().remove(item);

			return null;
		});
	}

	@Override
	public void saveDataStorage() {
		bindSession();

		getSession().flush();
	}

	@Override
	public void clearDataStorage() {
		bindSession();

		// em.clear();
		getSession().clear();
	}

	@Override
	public <T extends Item> void initItem(final T item) {
		for (final Field field : ClassUtil.getFieldsWithAnnotation(item.getClass(), Property.class)) {
			if (field.getType().isAssignableFrom(List.class)) {
				ClassUtil.setField(item, field.getName(), new ArrayList<>());
			} else if (field.getType().isAssignableFrom(Set.class)) {
				ClassUtil.setField(item, field.getName(), new HashSet<>());
			} else if (field.getType().isAssignableFrom(Map.class)) {
				ClassUtil.setField(item, field.getName(), new HashMap<>());
			}
		}
	}

	@Override
	public <T extends Item> void detach(final List<T> items) {
		bindSession();

		for (final T item : items) {
			// HibernateUtil.initializeObject(item, "my.app.model");

			// em.detach(item);
			getSession().detach(item);
		}
	}

	protected Session getSession() {
		EntityManagerHolder holder = ((EntityManagerHolder) TransactionSynchronizationManager
				.getResource(entityManagerFactory));

		if (holder != null) {
			return holder.getEntityManager().unwrap(Session.class);
		}

		throw new IllegalStateException("Could not fetch persistence entity manager");
	}

	protected void bindSession() {
		if (!TransactionSynchronizationManager.hasResource(entityManagerFactory)) {
			TransactionSynchronizationManager.bindResource(entityManagerFactory,
					new EntityManagerHolder(entityManagerFactory.createEntityManager()));
		}
	}

	protected void unbindSession() {
		final EntityManagerHolder emHolder = (EntityManagerHolder) TransactionSynchronizationManager
				.unbindResource(entityManagerFactory);
		EntityManagerFactoryUtils.closeEntityManager(emHolder.getEntityManager());
	}

	public EntityManagerFactory getEntityManagerFactory() {
		return entityManagerFactory;
	}

	public SessionFactory getSessionFactory() {
		return entityManagerFactory.unwrap(SessionFactory.class);
	}

}
