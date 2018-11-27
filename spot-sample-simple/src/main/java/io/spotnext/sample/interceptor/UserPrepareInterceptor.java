package io.spotnext.sample.interceptor;

import org.springframework.beans.factory.annotation.Autowired;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import io.spotnext.core.infrastructure.exception.ItemInterceptorException;
import io.spotnext.core.infrastructure.interceptor.ItemPrepareInterceptor;
import io.spotnext.core.infrastructure.interceptor.impl.AbstractItemInterceptor;
import io.spotnext.core.persistence.exception.SequenceAccessException;
import io.spotnext.core.persistence.service.SequenceGenerator;
import io.spotnext.itemtype.core.user.User;

@Service
public class UserPrepareInterceptor extends AbstractItemInterceptor<User> implements ItemPrepareInterceptor<User> {

	@Autowired
	protected SequenceGenerator sequenceGenerator;

	@Override
	public Class<User> getItemType() {
		return User.class;
	}

	@Override
	public void onPrepare(final User user) throws ItemInterceptorException {
		try {
			if (StringUtils.isBlank(user.getUid())) {
				user.getUid("user-" + sequenceGenerator.getNextSequenceValue("user-sequence"));
			}
		} catch (final SequenceAccessException e) {
			throw new ItemInterceptorException(e.getMessage(), e);
		}
	}
}
