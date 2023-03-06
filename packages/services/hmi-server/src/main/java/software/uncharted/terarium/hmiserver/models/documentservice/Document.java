package software.uncharted.terarium.hmiserver.models.documentservice;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * XDD Document representation
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class Document implements Serializable {

	@JsonAlias("_gddid")
	private String gddId;

	private String title;

	@JsonProperty("abstract")
	private String abstractText;

	private String journal;

	private String type;

	private String number;

	private String pages;

	private String publisher;

	private String volume;

	private String year;

	private List<Map<String, String>> link;

	private List<Map<String, String>> author;

	private List<Map<String, String>> identifier;

	@JsonAlias("known_terms")
	private Map<String, List<String>> knownTerms;

	@JsonAlias("_highlight")
	private List<String> highlight;

	@JsonAlias("related_documents")
	private List<Document> relatedDocuments;

	@JsonAlias("related_extractions")
	private List<Extraction> relatedExtractions;

	@JsonAlias("known_entities")
	private KnownEntities knownEntities;

	@JsonAlias("citation_list")
	private List<Map<String, String>> citationList;


}

