package io.spotnext.core.persistence.service.impl;

import java.util.concurrent.Callable;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionUsageException;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.spotnext.core.infrastructure.service.impl.AbstractService;
import io.spotnext.core.persistence.service.TransactionService;

/**
 * <p>DefaultTransactionService class.</p>
 *
 * @author mojo2012
 * @version 1.0
 * @since 1.0
 */
@SuppressFBWarnings(value = "UWF_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR", justification = "Initialized by spring post construct")
@Service
public class DefaultTransactionService extends AbstractService implements TransactionService {

	@Resource
	protected PlatformTransactionManager transactionManager;

	protected TransactionTemplate transactionTemplate;
	protected ThreadLocal<TransactionStatus> currentTransaction = new ThreadLocal<>();

	/**
	 * <p>setup.</p>
	 */
	@PostConstruct
	public void setup() {
		transactionTemplate = new TransactionTemplate(transactionManager);
		transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	/** {@inheritDoc} */
	@Override
	public <R> R execute(Callable<R> body) throws TransactionException {
		boolean transactionWasAlreadyActive = isTransactionActive();

		if (!transactionWasAlreadyActive) {
			start();
		}

		boolean commit = true;

		try {
			return body.call();
		} catch (Exception e) {
			rollback();
			commit = false;

			throw new TransactionUsageException("Error during transactional execution.", e);
		} finally {
			if (!transactionWasAlreadyActive && commit) {
				commit();
			}
		}
	}

	/** {@inheritDoc} */
	@Override
	public void executeWithoutResult(Runnable body) throws TransactionException {
		execute(() -> {
			body.run();
			return null;
		});
	}

	/** {@inheritDoc} */
	@Override
	public void start() throws TransactionException {
		if (currentTransaction.get() == null) {
			TransactionDefinition def = new DefaultTransactionDefinition();
			TransactionStatus status = transactionManager.getTransaction(def);

			loggingService.debug(String.format("Creating new transaction for thread %s (id = %s)",
					Thread.currentThread().getName(), Thread.currentThread().getId()));

			currentTransaction.set(status);
		} else {
			throw new CannotCreateTransactionException("There is already an active transaction.");
			// loggingService.debug("There is already a transaction running");
		}
	}

	/**
	 * <p>createSavePoint.</p>
	 *
	 * @return a {@link java.lang.Object} object.
	 * @throws org.springframework.transaction.TransactionException if any.
	 */
	public Object createSavePoint() throws TransactionException {
		if (currentTransaction.get() != null) {
			return currentTransaction.get().createSavepoint();
		} else {
			throw new CannotCreateTransactionException("Cannot create savepoint as there is no active transaction.");
		}
	}

	/** {@inheritDoc} */
	@Override
	public void commit() throws TransactionException {
		if (currentTransaction.get() != null) {
			TransactionStatus status = currentTransaction.get();
			transactionManager.commit(status);
			currentTransaction.remove();
		} else {
			loggingService.warn("Cannot commit: no transaction active.");
		}
	}

	/** {@inheritDoc} */
	@Override
	public void rollback() throws TransactionException {
		if (currentTransaction.get() != null) {
			TransactionStatus status = currentTransaction.get();
			transactionManager.rollback(status);
			currentTransaction.remove();
		} else {
			loggingService.warn("Cannot roleback: no transaction active.");
		}
	}

	// public void rollbackToSavePoint(Object savePoint) throws TransactionException
	// {
	// if (currentTransaction.get() != null) {
	// TransactionStatus status =
	// currentTransaction.get().rollbackToSavepoint(savepoint);
	// } else {
	// throw new UnexpectedRollbackException("There is no active transaction.");
	// }
	// }

	/** {@inheritDoc} */
	@Override
	public boolean isTransactionActive() {
		return currentTransaction.get() != null;
	}
}
