package software.uncharted.terarium.hmiserver.models.dataservice.dataset;

import java.io.Serial;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.JsonNode;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import software.uncharted.terarium.hmiserver.annotations.TSModel;
import software.uncharted.terarium.hmiserver.annotations.TSOptional;
import software.uncharted.terarium.hmiserver.models.TerariumAsset;
import software.uncharted.terarium.hmiserver.models.dataservice.Grounding;
import software.uncharted.terarium.hmiserver.models.dataservice.JsonConverter;
import software.uncharted.terarium.hmiserver.models.dataservice.ObjectConverter;

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
	 * (Optional) List of urls from which the dataset can be downloaded/fetched.
	 */
	@TSOptional
	private List<String> datasetUrls;

	/** Information regarding the columns that make up the dataset */
	@TSOptional
	@Convert(converter = ObjectConverter.class)
	@JdbcTypeCode(SqlTypes.JSON)
	private List<DatasetColumn> columns;

	/** (Optional) Unformatted metadata about the dataset */
	@TSOptional
	@Convert(converter = JsonConverter.class)
	@Column(columnDefinition = "text")
	private JsonNode metadata;

	/** (Optional) Source of dataset */
	@TSOptional
	private String source;

	/**
	 * (Optional) Grounding of ontological concepts related to the dataset as a
	 * whole
	 */
	@TSOptional
	@Convert(converter = ObjectConverter.class)
	@JdbcTypeCode(SqlTypes.JSON)
	private Grounding grounding;

	@Override
	public Dataset clone() {
		final Dataset clone = new Dataset();

		cloneSuperFields(clone);

		clone.userId = this.userId;
		clone.esgfId = this.esgfId;
		clone.dataSourceDate = this.dataSourceDate;
		clone.fileNames = this.fileNames != null ? new ArrayList<>(this.fileNames) : null;
		clone.datasetUrls = this.datasetUrls != null ? new ArrayList<>(this.datasetUrls) : null;
		clone.columns = this.columns != null ? new ArrayList<>(this.columns) : null;
		clone.metadata = this.metadata;
		clone.source = this.source;
		clone.grounding = this.grounding.clone();

		return clone;
	}
}
