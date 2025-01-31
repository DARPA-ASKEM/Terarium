package software.uncharted.terarium.hmiserver.models.dataservice.dataset;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.experimental.Accessors;
import software.uncharted.terarium.hmiserver.annotations.TSModel;

@Data
@Accessors(chain = true)
@TSModel
public class DatasetColumnStats {

	private NumericColumnStats numericStats;
	private NonNumericColumnStats nonNumericStats;

	@Data
	public static class NumericColumnStats {

		@JsonProperty("data_type")
		private String dataType;

		private double mean;
		private double median;
		private double min;
		private double max;

		@JsonProperty("std_dev")
		private double stdDev;

		private double[] quartiles;

		@JsonProperty("unique_values")
		private int uniqueValues;

		@JsonProperty("missing_values")
		private int missingValues;

		@JsonProperty("histogram_bins")
		private HistogramBin[] histogramBins;
	}

	@Data
	public static class NonNumericColumnStats {

		@JsonProperty("data_type")
		private String dataType;

		@JsonProperty("unique_values")
		private int uniqueValues;

		@JsonProperty("most_common")
		private Map<String, Integer> mostCommon;

		@JsonProperty("missing_values")
		private int missingValues;
	}

	@Data
	public static class HistogramBin {

		private double start;
		private double end;
		private int count;
	}
}
