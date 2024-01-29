package org.fiware.vc.it.model;

import lombok.Data;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class AllowedRoles {

	private List<String> names;
	private String target;
}
