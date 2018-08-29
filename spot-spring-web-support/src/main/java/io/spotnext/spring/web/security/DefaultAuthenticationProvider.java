package io.spotnext.spring.web.security;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import io.spotnext.core.infrastructure.service.ConfigurationService;
import io.spotnext.core.infrastructure.service.SessionService;
import io.spotnext.core.infrastructure.service.UserService;
import io.spotnext.core.security.service.AuthenticationService;
import io.spotnext.itemtype.core.user.User;
import io.spotnext.itemtype.core.user.UserGroup;
import io.spotnext.spring.web.security.exception.AuthenticationException;

/**
 * Authenticates users using the
 * {@link io.spotnext.core.security.service.AuthenticationService} and the
 * configured admin config property.
 *
 * @author mojo2012
 * @version 1.0
 * @since 1.0
 */
public class DefaultAuthenticationProvider implements AuthenticationProvider {

	/**
	 * Constant
	 * <code>ADMIN_USER_NAME_KEY="security.authentication.admin.username"</code>
	 */
	public static final String ADMIN_USER_NAME_KEY = "security.authentication.admin.username";
	/** Constant <code>DEFAULT_ADMIN_USER_NAME="admin"</code> */
	public static final String DEFAULT_ADMIN_USER_NAME = "admin";

	@Autowired
	protected AuthenticationService authenticationService;

	@Autowired
	protected ConfigurationService configurationService;

	@Autowired
	protected SessionService sessionService;

	@Autowired
	protected UserService<User, UserGroup> userService;

	/**
	 * {@inheritDoc}
	 *
	 * Authenticates the given user and the credentials using the
	 * {@link AuthenticationService}.
	 * 
	 * @param authentication the authentication object
	 */
	@Override
	public Authentication authenticate(final Authentication authentication) throws AuthenticationException {
		final String name = authentication.getName();
		final String password = authentication.getCredentials().toString();

		final User user = authenticationService.getAuthenticatedUser(name, password, false);

		if (user != null) {
			// set the authenticated user to the current session
			userService.setCurrentUser(user);

			final List<GrantedAuthority> grantedAuths = new ArrayList<>();

			// add admin role
			if (isAdminUser(user)) {
				grantedAuths.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
			}

			// always add user role (also for admins)
			grantedAuths.add(new SimpleGrantedAuthority("ROLE_USER"));

			final UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(name, password,
					grantedAuths);

			final UserDetails userDetails = new org.springframework.security.core.userdetails.User(user.getId(),
					user.getPassword(), grantedAuths);
			auth.setDetails(userDetails);

			return auth;
		} else {
			throw new AuthenticationException("User could not be authenticated.");
		}
	}

	/**
	 * Compares the given {@link User#getId()} against the
	 * {@link DefaultAuthenticationProvider#ADMIN_USER_NAME_KEY} using
	 * {@link DefaultAuthenticationProvider#DEFAULT_ADMIN_USER_NAME} as fallback. If
	 * the username matches, then the user will be given the admin role.
	 * 
	 * @param user the user to check
	 * @return true if the given user is admin
	 */
	protected boolean isAdminUser(final User user) {
		final String adminUserName = configurationService.getString(ADMIN_USER_NAME_KEY, DEFAULT_ADMIN_USER_NAME);

		return StringUtils.equals(user.getId(), adminUserName);
	}

	/** {@inheritDoc} */
	@Override
	public boolean supports(final Class<?> authentication) {
		return authentication.equals(UsernamePasswordAuthenticationToken.class);
	}
}
