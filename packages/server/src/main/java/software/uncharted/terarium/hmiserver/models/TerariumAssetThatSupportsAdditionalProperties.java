package software.uncharted.terarium.hmiserver.models;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import software.uncharted.terarium.hmiserver.annotations.TSIgnore;

import java.util.HashMap;
import java.util.Map;

public class TerariumAssetThatSupportsAdditionalProperties extends TerariumAsset {

	@TSIgnore
	public Map<String, Object> additionalProperties = new HashMap<>();

	@JsonAnyGetter
	@TSIgnore
	public Map<String, Object> getAdditionalProperties() {
		return additionalProperties;
	}

	@JsonAnySetter
	@TSIgnore
	public void setAdditionalProperties(final String name, final Object value) {
		additionalProperties.put(name, value);
	}
}
