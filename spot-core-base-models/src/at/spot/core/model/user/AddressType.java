package at.spot.core.model.user;

import javax.validation.constraints.NotNull;

import at.spot.core.infrastructure.annotation.ItemType;
import at.spot.core.infrastructure.annotation.Property;
import at.spot.core.model.Item;
import at.spot.core.model.ItemTypeConstants;

@ItemType(typeCode = ItemTypeConstants.ADDRESS_TYPE)
public class AddressType extends Item {

	private static final long serialVersionUID = 1L;

	@NotNull
	@Property(unique = true)
	public String code;
}