package software.uncharted.terarium.hmiserver.models.dataservice.dataset;

import java.io.Serial;
import java.sql.Timestamp;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.JsonNode;

import jakarta.persistence.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import software.uncharted.terarium.hmiserver.annotations.TSModel;
import software.uncharted.terarium.hmiserver.annotations.TSOptional;
import software.uncharted.terarium.hmiserver.models.TerariumAsset;
import software.uncharted.terarium.hmiserver.models.dataservice.Grounding;

/** Represents a dataset document from TDS */
@EqualsAndHashCode(callSuper = true)
@Data
@Accessors(chain = true)
@TSModel
@Entity
public class Dataset extends TerariumAsset {

	@Serial
	private static final long serialVersionUID = 6927286281160755696L;

	/**
	 * UserId of the user who created the dataset
	 */
	@TSOptional
	private String userId;

	/**
	 * ESGF id of the dataset. This will be null for datasets that are not from ESGF
	 */
	@TSOptional
	private String esgfId;

	/** (Optional) data source date */
	@TSOptional
	@JsonAlias("data_source_date")
	private Timestamp dataSourceDate;

	/** (Optional) list of file names associated with the dataset */
	@TSOptional
	@JsonAlias("file_names")
	private List<String> fileNames;

	/**
	 * (Optional) Url from which the dataset can be downloaded/fetched TODO: IS THIS
	 * NEEDED? IS THIS FROM OLD TDS?
	 * https://github.com/DARPA-ASKEM/terarium/issues/3194
	 */
	@TSOptional
	@JsonAlias("dataset_url")
	private String datasetUrl;

	/**
	 * (Optional) List of urls from which the dataset can be downloaded/fetched.
	 * Used for ESGF datasets
	 */
	@TSOptional
	private List<String> datasetUrls;

	/** Information regarding the columns that make up the dataset */
	@TSOptional
	private List<DatasetColumn> columns;

	/** (Optional) Unformatted metadata about the dataset */
	@TSOptional
	private JsonNode metadata;

	/** (Optional) Source of dataset */
	@TSOptional
	private String source;

	/**
	 * (Optional) Grounding of ontological concepts related to the dataset as a
	 * whole
	 */
	@TSOptional
	private Grounding grounding;
}
