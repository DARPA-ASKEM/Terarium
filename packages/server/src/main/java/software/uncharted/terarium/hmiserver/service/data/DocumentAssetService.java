package software.uncharted.terarium.hmiserver.service.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.observation.annotation.Observed;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.uncharted.terarium.hmiserver.configuration.Config;
import software.uncharted.terarium.hmiserver.models.dataservice.document.DocumentAsset;
import software.uncharted.terarium.hmiserver.repository.data.DocumentRepository;
import software.uncharted.terarium.hmiserver.service.s3.S3ClientService;
import software.uncharted.terarium.hmiserver.utils.rebac.Schema;

@Slf4j
@Service
public class DocumentAssetService extends TerariumAssetService<DocumentAsset, DocumentRepository> {

	public DocumentAssetService(
		final ObjectMapper objectMapper,
		final Config config,
		final ProjectService projectService,
		final ProjectAssetService projectAssetService,
		final DocumentRepository repository,
		final S3ClientService s3ClientService
	) {
		super(objectMapper, config, projectService, projectAssetService, repository, s3ClientService, DocumentAsset.class);
	}

	@Override
	@Observed(name = "function_profile")
	protected String getAssetPath() {
		return config.getDocumentPath();
	}

	@Override
	@Observed(name = "function_profile")
	public DocumentAsset createAsset(
		final DocumentAsset asset,
		final UUID projectId,
		final Schema.Permission hasWritePermission
	) throws IOException {
		return super.createAsset(asset, projectId, hasWritePermission);
	}

	@Override
	@Observed(name = "function_profile")
	public Optional<DocumentAsset> updateAsset(
		final DocumentAsset asset,
		final UUID projectId,
		final Schema.Permission hasWritePermission
	) throws IOException, IllegalArgumentException {
		final Optional<DocumentAsset> originalOptional = getAsset(asset.getId(), hasWritePermission);
		if (originalOptional.isEmpty()) {
			return Optional.empty();
		}

		final DocumentAsset original = originalOptional.get();

		// Preserve ownership. This may be coming from KM which doesn't have an
		// awareness of who owned this document.
		asset.setUserId(original.getUserId());

		final Optional<DocumentAsset> updatedOptional = super.updateAsset(asset, projectId, hasWritePermission);
		if (updatedOptional.isEmpty()) {
			return Optional.empty();
		}

		return updatedOptional;
	}
}
