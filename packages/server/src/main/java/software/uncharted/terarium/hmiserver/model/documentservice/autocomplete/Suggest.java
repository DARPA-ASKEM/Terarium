package software.uncharted.terarium.hmiserver.model.documentservice.autocomplete;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.List;

@Data
@Accessors(chain = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Suggest implements Serializable {

	@JsonAlias("entity-suggest-fuzzy")
	private List<EntitySuggestFuzzy> entitySuggestFuzzy;


}
