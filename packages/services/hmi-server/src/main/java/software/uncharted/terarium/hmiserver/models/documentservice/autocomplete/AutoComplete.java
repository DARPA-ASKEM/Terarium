package software.uncharted.terarium.hmiserver.models.documentservice.autocomplete;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;


@Data
@Accessors(chain = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AutoComplete implements Serializable {

	private Suggest suggest;

	/**
	 * Looks at the underlying objects returned by XDD and determines if there are any auto complete suggestions.
	 * This is a convenience method meant to make returning error codes cleaner.
	 *
	 * @return true if there are no suggestions.
	 */
	public boolean hasNoSuggestions() {
		return (suggest == null
			|| suggest.getEntitySuggestFuzzy() == null
			|| suggest.getEntitySuggestFuzzy().isEmpty()
			|| suggest.getEntitySuggestFuzzy().get(0).getOptions() == null
			|| suggest.getEntitySuggestFuzzy().get(0).getOptions().isEmpty());
	}

}
