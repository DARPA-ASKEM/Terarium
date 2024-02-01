package software.uncharted.terarium.esingest.models.input.covid;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CovidDocument implements Serializable {

	@Data
	@JsonIgnoreProperties(ignoreUnknown = true)
	static public class Source implements Serializable {
		private String title;

		private String body;

		private Feature feature;
	}

	@Data
	@JsonIgnoreProperties(ignoreUnknown = true)
	static public class Feature implements Serializable {
		private List<Timestamp> date;

		private List<String> website;

		private List<String> doi;

		private List<String> language;

		private List<String> version;

		private List<String> pubname;

		private List<String> organization;

		private List<String> name;
	}

	@JsonAlias("_id")
	String id;

	@JsonAlias("_source")
	Source source;
}
