package software.uncharted.terarium.hmiserver.models.dataservice.document;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import java.io.Serial;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.hibernate.annotations.Type;
import software.uncharted.terarium.hmiserver.annotations.TSModel;
import software.uncharted.terarium.hmiserver.annotations.TSOptional;
import software.uncharted.terarium.hmiserver.models.TerariumAsset;
import software.uncharted.terarium.hmiserver.models.dataservice.Grounding;

@EqualsAndHashCode(callSuper = true)
@Data
@TSModel
@Accessors(chain = true)
@Entity
public class DocumentAsset extends TerariumAsset {

	@Serial
	private static final long serialVersionUID = -8425680186002783351L;

	@TSOptional
	@Column(length = 255)
	private String userId;

	@TSOptional
	@JsonAlias("document_url")
	@Column(length = 1024)
	private String documentUrl;

	@TSOptional
	@Type(JsonType.class)
	@Column(columnDefinition = "json")
	private Map<String, JsonNode> metadata;

	@TSOptional
	@Column(columnDefinition = "text")
	private String source;

	@TSOptional
	@Column(columnDefinition = "text")
	private String text;

	@TSOptional
	@OneToOne(fetch = FetchType.EAGER, cascade = CascadeType.ALL)
	@JoinColumn(name = "grounding_id")
	private Grounding grounding;

	@TSOptional
	@Column(columnDefinition = "text")
	private String documentAbstract;

	@TSOptional
	@Type(JsonType.class)
	@Column(columnDefinition = "json")
	@Deprecated
	private List<DocumentExtraction> assets;

	@TSOptional
	@Type(JsonType.class)
	@Column(columnDefinition = "json")
	private List<ExtractedDocumentPage> extractions = new ArrayList<>();

	@Override
	public List<String> getFileNames() {
		if (this.fileNames == null) {
			this.fileNames = new ArrayList<>();
		}

		// ensure these are included in filenames
		if (this.assets != null) {
			for (final DocumentExtraction asset : assets) {
				if (!this.fileNames.contains(asset.getFileName())) {
					this.fileNames.add(asset.getFileName());
				}
			}
		}
		return this.fileNames;
	}

	@Override
	public DocumentAsset clone() {
		final DocumentAsset clone = new DocumentAsset();
		super.cloneSuperFields(clone);

		clone.documentUrl = this.documentUrl;

		if (this.metadata != null) {
			clone.metadata = new HashMap<>();
			for (final String key : this.metadata.keySet()) {
				// I don't like that this is an "object" because it doesn't clone nicely...
				clone.metadata.put(key, this.metadata.get(key).deepCopy());
			}
		}

		clone.source = this.source;
		clone.text = this.text;
		for (final ExtractedDocumentPage extraction : this.extractions) {
			clone.extractions.add(extraction.clone());
		}

		if (this.grounding != null) {
			clone.grounding = this.grounding.clone();
		}

		if (this.assets != null) {
			clone.assets = new ArrayList<>();
			for (final DocumentExtraction asset : this.assets) {
				clone.assets.add(asset.clone());
			}
		}

		return clone;
	}
}
