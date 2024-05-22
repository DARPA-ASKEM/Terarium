package software.uncharted.terarium.hmiserver.models.dataservice.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.Serial;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.hibernate.annotations.Type;
import software.uncharted.terarium.hmiserver.annotations.TSIgnore;
import software.uncharted.terarium.hmiserver.annotations.TSModel;
import software.uncharted.terarium.hmiserver.annotations.TSOptional;
import software.uncharted.terarium.hmiserver.models.TerariumAssetThatSupportsAdditionalProperties;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.ModelHeader;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.ModelMetadata;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.ModelParameter;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.ModelSemantics;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.semantics.Initial;

@EqualsAndHashCode(callSuper = true)
@Data
@Accessors(chain = true)
@Entity
@TSModel
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
	private ModelSemantics semantics;

	@TSOptional
	@Type(JsonType.class)
	@Column(columnDefinition = "json")
	private ModelMetadata metadata;


	@Override
	public Model clone(){
		final Model clone = new Model();
		super.cloneSuperFields(clone);

		if(header != null){
			clone.header = header.clone();
		}
		clone.userId = this.userId;

		if(model != null){
			clone.model = new HashMap<>();
			for(final Map.Entry<String, JsonNode> entry : model.entrySet()){
				clone.model.put(entry.getKey(), entry.getValue().deepCopy());
			}
		}

		if(properties != null){
			clone.properties = properties.deepCopy();
		}

		if(semantics != null){
			clone.semantics = semantics.clone();
		}

		if(metadata != null){
			clone.metadata = metadata.clone();
		}

		return clone;
	}


	@JsonIgnore
	@TSIgnore
	public List<ModelParameter> getParameters() {
		final ObjectMapper objectMapper = new ObjectMapper();
		if (this.isRegnet()) {
			return objectMapper.convertValue(
					this.getModel().get("parameters"), new TypeReference<>() {});
		} else {
			return this.getSemantics().getOde().getParameters();
		}
	}

	@JsonIgnore
	@TSIgnore
	public List<Initial> getInitials() {
		final ObjectMapper objectMapper = new ObjectMapper();
		if (this.isRegnet()) {
			return objectMapper.convertValue(this.getModel().get("initials"), new TypeReference<>() {});
		} else {
			return this.getSemantics().getOde().getInitials();
		}
	}

	@JsonIgnore
	@TSIgnore
	public boolean isRegnet() {
		return this.getHeader().getSchemaName().equalsIgnoreCase("regnet");
	}
}
