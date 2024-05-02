package software.uncharted.terarium.hmiserver.models.dataservice.dataset;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAlias;

import lombok.Data;
import lombok.experimental.Accessors;
import software.uncharted.terarium.hmiserver.annotations.TSModel;
import software.uncharted.terarium.hmiserver.annotations.TSOptional;
import software.uncharted.terarium.hmiserver.models.dataservice.Grounding;

/** Represents a column in a dataset */
@Data
@Accessors(chain = true)
@TSModel
public class DatasetColumn {

	/** Name of the column */
	private String name;

	/**
	 * Datatype. One of: unknown, boolean, string, char, integer, int, float,
	 * double, timestamp, datetime, date, time
	 */
	@JsonAlias("data_type")
	private ColumnType dataType;

	/** (Optional) String that describes the formatting of the value */
	@TSOptional
	@JsonAlias("format_str")
	private String formatStr;

	/** Column annotations from the MIT data profiling tool */
	private List<String> annotations;

	/** (Optional) Unformatted metadata about the dataset */
	@TSOptional
	private Map<String, Object> metadata;

	/** (Optional) Grounding of ontological concepts related to the column */
	@TSOptional
	private Grounding grounding;

	@TSOptional
	private String description;

	enum ColumnType {
		@JsonAlias("unknown")
		UNKNOWN,
		@JsonAlias("boolean")
		BOOLEAN,
		@JsonAlias("string")
		STRING,
		@JsonAlias("char")
		CHAR,
		@JsonAlias("integer")
		INTEGER,
		@JsonAlias("int")
		INT,
		@JsonAlias("float")
		FLOAT,
		@JsonAlias("double")
		DOUBLE,
		@JsonAlias("timestamp")
		TIMESTAMP,
		@JsonAlias("datetime")
		DATETIME,
		@JsonAlias("date")
		DATE,
		@JsonAlias("time")
		TIME
	}

	public void updateMetadata(final Map<String, Object> metadata) {
		if (this.metadata == null) {
			this.metadata = metadata;
		} else {
			this.metadata.putAll(metadata);
		}
	}

	public DatasetColumn clone() {
		final DatasetColumn clone = new DatasetColumn();

		clone.name = this.name;
		clone.dataType = this.dataType;
		clone.formatStr = this.formatStr;
		clone.annotations = this.annotations != null ? List.copyOf(this.annotations) : null;
		clone.metadata = this.metadata != null ? Map.copyOf(this.metadata) : null;
		clone.grounding = this.grounding != null ? this.grounding.clone() : null;
		clone.description = this.description;

		return clone;
	}
}
