package software.uncharted.terarium.hmiserver.models.dataservice.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import java.io.Serial;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.Type;
import software.uncharted.terarium.hmiserver.annotations.TSIgnore;
import software.uncharted.terarium.hmiserver.annotations.TSModel;
import software.uncharted.terarium.hmiserver.annotations.TSOptional;
import software.uncharted.terarium.hmiserver.models.TerariumAssetEmbeddingType;
import software.uncharted.terarium.hmiserver.models.TerariumAssetThatSupportsAdditionalProperties;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.ModelHeader;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.ModelMetadata;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.ModelParameter;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.ModelSemantics;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.semantics.Initial;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.semantics.Observable;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.semantics.OdeSemantics;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.semantics.Rate;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.semantics.State;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.semantics.Transition;
import software.uncharted.terarium.hmiserver.models.dataservice.regnet.RegNetVertex;
import software.uncharted.terarium.hmiserver.utils.JsonUtil;

@EqualsAndHashCode(callSuper = true)
@Data
@Accessors(chain = true)
@Entity
@TSModel
@Slf4j
public class Model extends TerariumAssetThatSupportsAdditionalProperties {

	@Serial
	private static final long serialVersionUID = 398195277271188277L;

	@Type(JsonType.class)
	@Column(columnDefinition = "json")
	private ModelHeader header;

	@TSOptional
	@Column(length = 255)
	private String userId;

	@Type(JsonType.class)
	@Column(columnDefinition = "json")
	private Map<String, JsonNode> model;

	@TSOptional
	@Type(JsonType.class)
	@Column(columnDefinition = "json")
	private JsonNode properties;

	@TSOptional
	@Type(JsonType.class)
	@Column(columnDefinition = "json")
	private ModelSemantics semantics = new ModelSemantics();

	@TSOptional
	@Type(JsonType.class)
	@Column(columnDefinition = "json")
	private ModelMetadata metadata;

	public void retainMetadataFields(final Model other) {
		final Map<String, JsonNode> props = getAdditionalProperties();
		final Map<String, JsonNode> otherProps = other.getAdditionalProperties();

		if (metadata == null) {
			metadata = other.getMetadata();
		} else {
			metadata.retainMetadataFields(other.getMetadata());
		}

		if (getDescription() == null) {
			setDescription(other.getDescription());
		}

		final List<String> propertiesToPreserve = List.of(
			"states",
			"metadata",
			"units",
			"vertices",
			"edges",
			"parameters",
			"initials"
		);

		for (final String property : propertiesToPreserve) {
			if (!otherProps.containsKey(property) || !otherProps.get(property).isArray()) {
				continue;
			}

			if (!props.containsKey(property) || !props.get(property).isArray()) {
				continue;
			}

			final ArrayNode otherProperty = (ArrayNode) otherProps.get(property);
			final ArrayNode thisProperty = (ArrayNode) props.get(property);

			for (final JsonNode element : otherProperty) {
				final JsonNode matching = JsonUtil.getFirstByPredicate(thisProperty, (final JsonNode node) -> {
					// Check if the 'state' object has a 'name' field
					return node.has("id");
				});

				if (matching == null) {
					// does not exist in current model, we can add the old one, otherwise keep the
					// new one
					thisProperty.add(element);
				}
			}

			props.put(property, thisProperty);
		}
	}

	public ModelMetadata getMetadata() {
		if (metadata == null) {
			return new ModelMetadata();
		}
		return metadata;
	}

	@Override
	public Model clone() {
		final Model clone = new Model();
		super.cloneSuperFields(clone);

		if (header != null) {
			clone.header = header.clone();
		}
		clone.userId = this.userId;

		if (model != null) {
			clone.model = new HashMap<>();
			for (final Map.Entry<String, JsonNode> entry : model.entrySet()) {
				clone.model.put(entry.getKey(), entry.getValue().deepCopy());
			}
		}

		if (properties != null) {
			clone.properties = properties.deepCopy();
		}

		if (semantics != null) {
			clone.semantics = semantics.clone();
		}

		if (metadata != null) {
			clone.metadata = metadata.clone();
		}

		return clone;
	}

	@JsonIgnore
	@TSIgnore
	public List<State> getStates() {
		if (isRegnet()) return null;
		if (!getModel().containsKey("states")) return new ArrayList<>();
		final ObjectMapper objectMapper = new ObjectMapper();
		return objectMapper.convertValue(getModel().get("states"), new TypeReference<>() {});
	}

	@JsonIgnore
	@TSIgnore
	public void setStates(final List<State> states) {
		if (isRegnet()) return;
		final ObjectMapper objectMapper = new ObjectMapper();
		getModel().put("states", objectMapper.convertValue(states, JsonNode.class));
	}

	@JsonIgnore
	@TSIgnore
	public List<RegNetVertex> getVerticies() {
		if (!isRegnet()) return null;
		if (!getModel().containsKey("vertices")) return new ArrayList<>();
		final ObjectMapper objectMapper = new ObjectMapper();
		return objectMapper.convertValue(getModel().get("vertices"), new TypeReference<>() {});
	}

	@JsonIgnore
	@TSIgnore
	public void setVerticies(final List<RegNetVertex> verticies) {
		if (!isRegnet()) return;
		final ObjectMapper objectMapper = new ObjectMapper();
		getModel().put("vertices", objectMapper.convertValue(verticies, JsonNode.class));
	}

	@JsonIgnore
	@TSIgnore
	public List<Observable> getObservables() {
		if (this.getSemantics() == null) {
			this.setSemantics(new ModelSemantics());
		}
		if (this.getSemantics().getOde() == null) {
			this.getSemantics().setOde(new OdeSemantics());
		}
		if (this.getSemantics().getOde().getObservables() == null) {
			this.getSemantics().getOde().setObservables(new ArrayList<>());
		}
		return this.getSemantics().getOde().getObservables();
	}

	@JsonIgnore
	@TSIgnore
	public void setObservables(final List<Observable> observables) {
		if (this.getSemantics() == null) {
			this.setSemantics(new ModelSemantics());
		}
		if (this.getSemantics().getOde() == null) {
			this.getSemantics().setOde(new OdeSemantics());
		}
		this.getSemantics().getOde().setObservables(observables);
	}

	@JsonIgnore
	@TSIgnore
	public List<ModelParameter> getParameters() {
		final ObjectMapper objectMapper = new ObjectMapper();
		if (this.isRegnet()) {
			return objectMapper.convertValue(this.getModel().get("parameters"), new TypeReference<>() {});
		}
		if (this.getSemantics() == null) {
			this.setSemantics(new ModelSemantics());
		}
		if (this.getSemantics().getOde() == null) {
			this.getSemantics().setOde(new OdeSemantics());
		}
		if (this.getSemantics().getOde().getParameters() == null) {
			this.getSemantics().getOde().setParameters(new ArrayList<>());
		}
		return this.getSemantics().getOde().getParameters();
	}

	@JsonIgnore
	@TSIgnore
	public void setParameters(final List<ModelParameter> parameters) {
		final ObjectMapper objectMapper = new ObjectMapper();
		if (this.isRegnet()) {
			this.getModel().put("parameters", objectMapper.convertValue(parameters, JsonNode.class));
		} else {
			if (this.getSemantics() == null) {
				this.setSemantics(new ModelSemantics());
			}
			if (this.getSemantics().getOde() == null) {
				this.getSemantics().setOde(new OdeSemantics());
			}
			this.getSemantics().getOde().setParameters(parameters);
		}
	}

	@JsonIgnore
	@TSIgnore
	public List<Transition> getTransitions() {
		final ObjectMapper objectMapper = new ObjectMapper();
		return objectMapper.convertValue(this.getModel().get("transitions"), new TypeReference<>() {});
	}

	@JsonIgnore
	@TSIgnore
	public void setTransitions(final List<Transition> parameters) {
		final ObjectMapper objectMapper = new ObjectMapper();
		this.getModel().put("transitions", objectMapper.convertValue(parameters, JsonNode.class));
	}

	@JsonIgnore
	@TSIgnore
	public List<Initial> getInitials() {
		final ObjectMapper objectMapper = new ObjectMapper();
		if (this.isRegnet()) {
			return objectMapper.convertValue(this.getModel().get("initials"), new TypeReference<>() {});
		} else {
			if (this.getSemantics() == null) {
				this.setSemantics(new ModelSemantics());
			}
			if (this.getSemantics().getOde() == null) {
				this.getSemantics().setOde(new OdeSemantics());
			}
			if (this.getSemantics().getOde().getInitials() == null) {
				this.getSemantics().getOde().setInitials(new ArrayList<>());
			}
			return this.getSemantics().getOde().getInitials();
		}
	}

	@JsonIgnore
	@TSIgnore
	public List<Rate> getRates() {
		final ObjectMapper objectMapper = new ObjectMapper();
		if (this.isRegnet()) {
			return objectMapper.convertValue(this.getModel().get("initials"), new TypeReference<>() {});
		} else {
			if (this.getSemantics() == null) {
				this.setSemantics(new ModelSemantics());
			}
			if (this.getSemantics().getOde() == null) {
				this.getSemantics().setOde(new OdeSemantics());
			}
			if (this.getSemantics().getOde().getRates() == null) {
				this.getSemantics().getOde().setRates(new ArrayList<>());
			}
			return this.getSemantics().getOde().getRates();
		}
	}

	@JsonIgnore
	@TSIgnore
	public void setRates(final List<Rate> rates) {
		final ObjectMapper objectMapper = new ObjectMapper();
		if (this.isRegnet()) {
			this.getModel().put("rates", objectMapper.convertValue(rates, JsonNode.class));
		} else {
			if (this.getSemantics() == null) {
				this.setSemantics(new ModelSemantics());
			}
			if (this.getSemantics().getOde() == null) {
				this.getSemantics().setOde(new OdeSemantics());
			}
			this.getSemantics().getOde().setRates(rates);
		}
	}

	@JsonIgnore
	@TSIgnore
	public boolean isRegnet() {
		if (this.getHeader() == null) {
			return false;
		}
		if (this.getHeader().getSchemaName() == null) {
			return false;
		}
		return this.getHeader().getSchemaName().equalsIgnoreCase("regnet");
	}

	@JsonIgnore
	@TSIgnore
	public boolean isPetrinet() {
		if (this.getHeader() == null) {
			return false;
		}
		if (this.getHeader().getSchemaName() == null) {
			return false;
		}
		return this.getHeader().getSchemaName().equalsIgnoreCase("petrinet");
	}

	@JsonIgnore
	@TSIgnore
	public String getDescriptionAsReadableString() {
		if (getMetadata().getDescription() == null) {
			return null;
		}

		// remove image tags
		final String regex = "<img\\b[^>]*>(.*?)<\\/img>|<img\\b[^>]*\\/>";
		final Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
		final Matcher matcher = pattern.matcher(new String(getMetadata().getDescription()));
		return matcher.replaceAll("");
	}

	@JsonIgnore
	@TSIgnore
	public String getEmbeddingSourceText() {
		if (getMetadata().getDescription() != null) {
			return getDescriptionAsReadableString();
		} else {
			return "";
		}
	}

	@JsonIgnore
	@TSIgnore
	public Map<TerariumAssetEmbeddingType, String> getEmbeddingsSourceByType() {
		final Map<TerariumAssetEmbeddingType, String> sources = super.getEmbeddingsSourceByType();

		// Description are saved as base64 encoded strings, this returns a pure string.
		if (getMetadata().getDescription() != null) {
			sources.put(TerariumAssetEmbeddingType.DESCRIPTION, getDescriptionAsReadableString());
		}

		return sources;
	}
}
