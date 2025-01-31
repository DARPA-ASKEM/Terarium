package software.uncharted.terarium.hmiserver.models.dataservice.dataset;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.hibernate.annotations.Type;
import software.uncharted.terarium.hmiserver.annotations.TSIgnore;
import software.uncharted.terarium.hmiserver.annotations.TSModel;
import software.uncharted.terarium.hmiserver.annotations.TSOptional;
import software.uncharted.terarium.hmiserver.models.TerariumEntity;
import software.uncharted.terarium.hmiserver.models.dataservice.Grounding;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.semantics.GroundedSemantic;
import software.uncharted.terarium.hmiserver.utils.JsonUtil;

/** Represents a column in a dataset */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors
@TSModel
@Entity
public class DatasetColumn extends TerariumEntity implements GroundedSemantic {

	/** Name of the column */
	@Column(length = 255)
	private String name;

	@TSOptional
	private String conceptId;

	@TSOptional
	@ManyToOne
	@JsonBackReference
	private Dataset dataset;

	@Column(length = 255)
	private String fileName;

	/**
	 * Datatype. One of: unknown, boolean, string, char, integer, int, float, double, timestamp, datetime, date, time
	 */
	@JsonAlias("data_type")
	@Enumerated(EnumType.STRING)
	private ColumnType dataType;

	/** (Optional) Column statistics */
	@TSOptional
	@Column(columnDefinition = "json")
	@Type(JsonType.class)
	private DatasetColumnStats stats;

	/** (Optional) String that describes the formatting of the value */
	@TSOptional
	@JsonAlias("format_str")
	@Column(length = 255)
	private String formatStr;

	/** Column annotations from the MIT data profiling tool */
	@Column(columnDefinition = "json")
	@Type(JsonType.class)
	private List<String> annotations;

	/** (Optional) Unformatted metadata about the dataset */
	@TSOptional
	@Type(JsonType.class)
	@Column(columnDefinition = "json")
	private JsonNode metadata;

	/** (Optional) Grounding of ontological concepts related to the column */
	@TSOptional
	@OneToOne(fetch = FetchType.EAGER, cascade = CascadeType.ALL)
	@JoinColumn(name = "grounding_id")
	private Grounding grounding;

	@TSOptional
	@Column(columnDefinition = "text")
	private String description;

	public void updateMetadata(final JsonNode metadata) {
		if (this.metadata == null) {
			this.metadata = metadata;
		} else {
			JsonUtil.setAll((ObjectNode) this.metadata, metadata);
		}
	}

	@TSIgnore
	public String getConceptReference() {
		return conceptId;
	}

	@TSIgnore
	public void setConceptReference(String id) {
		this.conceptId = id;
	}

	@Override
	public DatasetColumn clone() {
		final DatasetColumn clone = new DatasetColumn();

		clone.name = this.name;
		clone.dataType = this.dataType;
		clone.formatStr = this.formatStr;
		if (this.annotations != null) {
			clone.annotations = new ArrayList<>();
			clone.annotations.addAll(this.annotations);
		}

		if (this.metadata != null) {
			clone.metadata = this.metadata.deepCopy();
		}

		if (this.grounding != null) clone.grounding = this.grounding.clone();
		if (this.stats != null) clone.stats = this.stats;

		clone.description = this.description;

		return clone;
	}

	public enum ColumnType {
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

	// Map Python data types to ColumnType
	public static ColumnType mapDataType(String pythonDataType) {
		if (pythonDataType == null) {
			return ColumnType.UNKNOWN;
		}

		// Convert to lowercase for case-insensitive matching
		String normalizedType = pythonDataType.toLowerCase();

		return switch (normalizedType) {
			// Integer types
			case "int64", "int32", "int16", "int8" -> ColumnType.INTEGER;
			// Float types
			case "float64", "float32", "float16" -> ColumnType.FLOAT;
			// Double types (often same as float in Pandas)
			case "float", "double" -> ColumnType.DOUBLE;
			// Boolean
			case "bool" -> ColumnType.BOOLEAN;
			// String types
			case "object", "string" -> ColumnType.STRING;
			// Date and Time types
			case "datetime64", "datetime" -> ColumnType.DATETIME;
			case "timedelta", "time" -> ColumnType.TIME;
			case "date" -> ColumnType.DATE;
			// Default case
			default -> ColumnType.UNKNOWN;
		};
	}
}
