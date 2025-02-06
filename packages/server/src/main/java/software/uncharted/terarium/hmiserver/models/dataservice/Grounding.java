package software.uncharted.terarium.hmiserver.models.dataservice;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import java.io.Serial;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.hibernate.annotations.Type;
import software.uncharted.terarium.hmiserver.annotations.TSIgnore;
import software.uncharted.terarium.hmiserver.annotations.TSModel;
import software.uncharted.terarium.hmiserver.annotations.TSOptional;
import software.uncharted.terarium.hmiserver.models.TerariumEntity;
import software.uncharted.terarium.hmiserver.models.mira.DKG;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TSModel
@Entity
@JsonIgnoreProperties(ignoreUnknown = true)
public class Grounding extends TerariumEntity {

	@Serial
	private static final long serialVersionUID = 302308407252037615L;

	/** Ontological identifier per DKG */
	@Type(JsonType.class)
	@Column(columnDefinition = "json")
	private Map<String, String> identifiers;

	/** (Optional) Additional stratification modifiers */
	@TSOptional
	@Type(JsonType.class)
	@Column(columnDefinition = "json")
	private Map<String, String> modifiers;

	/** Default constructor */
	public Grounding() {
		this.identifiers = new HashMap<>();
		this.modifiers = new HashMap<>();
	}

	/** Constructor from a DKG */
	public Grounding(DKG dkg) {
		this.identifiers = new HashMap<>();
		final String[] curie = dkg.getCurie().split(":");
		this.identifiers.put(curie[0], curie[1]);
		this.modifiers = new HashMap<>();
	}

	@Override
	public Grounding clone() {
		final Grounding clone = new Grounding();

		if (this.identifiers != null && !this.identifiers.isEmpty()) {
			clone.identifiers = new HashMap<>();
			clone.identifiers.putAll(this.identifiers);
		}
		if (this.modifiers != null && !this.modifiers.isEmpty()) {
			clone.modifiers = new HashMap<>();
			clone.modifiers.putAll(this.modifiers);
		}

		return clone;
	}

	@TSIgnore
	public Boolean isEmpty() {
		return this.identifiers.isEmpty() && this.modifiers.isEmpty();
	}
}
