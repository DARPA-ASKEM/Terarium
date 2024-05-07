package software.uncharted.terarium.hmiserver.models.dataservice;

import java.io.Serial;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.annotations.Type;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import software.uncharted.terarium.hmiserver.annotations.TSModel;
import software.uncharted.terarium.hmiserver.annotations.TSOptional;
import software.uncharted.terarium.hmiserver.models.BaseEntity;

/** Represents a grounding document from TDS */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TSModel
@Entity
public class Grounding extends BaseEntity {

	@Serial
	private static final long serialVersionUID = 302308407252037615L;

	/** Ontological identifier per DKG */
	@Type(JsonType.class)
	@Column(columnDefinition = "text")
	private List<Identifier> identifiers;

	/** (Optional) Additional context that informs the grounding */
	@TSOptional
	@Type(JsonType.class)
	@Column(columnDefinition = "text")
	private Map<String, Object> context;

	@Override
	public Grounding clone() {

		final Grounding clone = new Grounding();
		if (this.identifiers != null) {
			clone.identifiers = new ArrayList<>();
			clone.identifiers.addAll(this.identifiers);
		}
		if (this.context != null) {
			clone.context = new HashMap<>();
			for (final String key : this.context.keySet()) {
				clone.context.put(key, context.get(key));
			}
		}

		return clone;
	}
}
