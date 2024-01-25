package software.uncharted.terarium.hmiserver.models.dataservice.modelparts.metadata;

import com.fasterxml.jackson.annotation.JsonAlias;

import lombok.Data;
import lombok.experimental.Accessors;
import software.uncharted.terarium.hmiserver.annotations.AMRSchemaType;
import software.uncharted.terarium.hmiserver.annotations.TSOptional;
import software.uncharted.terarium.hmiserver.models.SupportAdditionalProperties;

@Data
@AMRSchemaType
@Accessors(chain = true)
public class Card implements SupportAdditionalProperties {
	@JsonAlias("DESCRIPTION")
	@TSOptional
	private String description;

	@JsonAlias("AUTHOR_INST")
	@TSOptional
	private String authorInst;

	@JsonAlias("AUTHOR_AUTHOR")
	@TSOptional
	private String authorAuthor;

	@JsonAlias("AUTHOR_EMAIL")
	@TSOptional
	private String authorEmail;

	@JsonAlias("DATE")
	@TSOptional
	private String date;

	@JsonAlias("SCHEMA")
	@TSOptional
	private String schema;

	@JsonAlias("PROVENANCE")
	@TSOptional
	private String provenance;

	@JsonAlias("DATASET")
	@TSOptional
	private String dataset;

	@JsonAlias("COMPLEXITY")
	@TSOptional
	private String complexity;

	@JsonAlias("USAGE")
	@TSOptional
	private String usage;

	@JsonAlias("LICENSE")
	@TSOptional
	private String license;
}
