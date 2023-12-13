package software.uncharted.terarium.hmiserver.models.dataservice.person;

import java.io.Serializable;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class Person implements Serializable {

	private UUID id;

	private String name;

	private String email;

	@JsonProperty("org")
	private String organization;

	private String website;

	@JsonProperty("is_registered")
	private Boolean isRegistered;
}
