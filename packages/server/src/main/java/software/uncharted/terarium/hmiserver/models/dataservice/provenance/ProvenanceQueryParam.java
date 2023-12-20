package software.uncharted.terarium.hmiserver.models.dataservice.provenance;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonAlias;

import lombok.Data;
import lombok.experimental.Accessors;
import software.uncharted.terarium.hmiserver.annotations.TSIgnore;
import software.uncharted.terarium.hmiserver.annotations.TSModel;
import software.uncharted.terarium.hmiserver.annotations.TSOptional;

@Data
@Accessors(chain = true)
@TSModel
public class ProvenanceQueryParam implements Serializable {

	@JsonAlias("root_id")
	private UUID rootId;

	@JsonAlias("root_type")
	private ProvenanceType rootType;

	@JsonAlias("user_id")
	private String userId;

	@TSIgnore
	private String curie;

	@TSOptional
	private Boolean nodes;

	@TSOptional
	private Boolean edges;

	@TSOptional
	private Boolean versions;

	@TSOptional
	private List<ProvenanceType> types;

	@TSOptional
	private Number hops;

	@TSOptional
	private Number limit;

	@TSOptional
	private Boolean verbose;

}
