package software.uncharted.terarium.hmiserver.models.documentservice.responses;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import software.uncharted.terarium.hmiserver.annotations.TSModel;
import software.uncharted.terarium.hmiserver.models.documentservice.Document;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@TSModel
public class DocumentsResponseOK extends XDDResponseOK implements Serializable {
	private List<Document> data;

	@JsonAlias("next_page")
	private String nextPage;

	private String scrollId;

	private Number hits;

	private Map<String, XDDFacetsItemResponse> facets;
}
