package software.uncharted.terarium.hmiserver.resources.documentservice.responses;

import lombok.Data;
import lombok.experimental.Accessors;

import javax.json.bind.annotation.JsonbProperty;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
public class XDDFacetsItemResponse implements Serializable {


	private Number docCountErrorUpperBound;


	private Number sumOtherDocCount;

	// The 'Object' in question here is a Number, however, sometimes they are coming in as Big numbers, and the
	// parent class can't handle the deserialization of scientific notation.
	private List<Map<String, Object>> buckets;

	@JsonbProperty("doc_count_error_upper_bound")
	private void setDocCountErrorUpperBound(Number docCountErrorUpperBound) {
		this.docCountErrorUpperBound = docCountErrorUpperBound;
	}

	@JsonbProperty("sum_other_doc_count")
	private void setSumOtherDocCount(Number sumOtherDocCount) {
		this.sumOtherDocCount = sumOtherDocCount;
	}
}
