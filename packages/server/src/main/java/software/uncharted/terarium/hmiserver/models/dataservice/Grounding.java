package software.uncharted.terarium.hmiserver.models.dataservice;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import java.io.Serial;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.hibernate.annotations.Type;
import software.uncharted.terarium.hmiserver.annotations.TSModel;
import software.uncharted.terarium.hmiserver.annotations.TSOptional;
import software.uncharted.terarium.hmiserver.models.TerariumEntity;
import software.uncharted.terarium.hmiserver.models.mira.DKG;

/** Represents a grounding document from TDS */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TSModel
@Entity
public class Grounding extends TerariumEntity {

	@Serial
	private static final long serialVersionUID = 302308407252037615L;

	/** Ontological identifier per DKG */
	@Type(JsonType.class)
	@Column(columnDefinition = "json")
	private List<DKG> identifiers;

	/** (Optional) Additional context that informs the grounding */
	@TSOptional
	@Type(JsonType.class)
	@Column(columnDefinition = "json")
	private Map<String, String> context;

	@Override
	public Grounding clone() {
		final Grounding clone = new Grounding();

		if (this.identifiers != null && !this.identifiers.isEmpty()) {
			clone.identifiers = new ArrayList<>();
			clone.identifiers.addAll(this.identifiers);
		}
		if (this.context != null && !this.context.isEmpty()) {
			clone.context = new HashMap<>();
			clone.context.putAll(this.context);
		}

		return clone;
	}
}
