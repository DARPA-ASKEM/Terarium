package software.uncharted.terarium.hmiserver.models.dataservice;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import software.uncharted.terarium.hmiserver.annotations.TSModel;
import software.uncharted.terarium.hmiserver.annotations.TSOptional;

@Data
@Accessors(chain = true)
@Slf4j
@TSModel
@AllArgsConstructor
public class CsvAsset implements Serializable {

	@Serial
	private static final long serialVersionUID = -3242849061655394707L;

	/** The csv data. Note that this may be incomplete if the dataset is too large. */
	List<List<String>> csv;

	/** Headers on this CSV file */
	List<String> headers;

	/** The number of rows in the CSV file. This may be a larger value than the csv object contained within */
	Integer rowCount;
}
