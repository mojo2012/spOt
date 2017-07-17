package at.spot.commerce.service;

import at.spot.core.infrastructure.service.UserService;
import at.spot.itemtype.commerce.customer.Customer;
import at.spot.itemtype.core.user.UserGroup;

/**
 * This service provides customer-oriented functionality, like password reset.
 *
 */
public interface CustomerService extends UserService<Customer, UserGroup> {

	/**
	 * Creates a password reset token for the given user. The token is only
	 * valid for a certain amount of time.
	 * 
	 * @param customer
	 * @return
	 */
	String createResetPasswordToken(Customer customer);

}