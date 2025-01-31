package software.uncharted.terarium.hmiserver.service.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.observation.annotation.Observed;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.apache.http.entity.ContentType;
import org.springframework.stereotype.Service;
import software.uncharted.terarium.hmiserver.configuration.Config;
import software.uncharted.terarium.hmiserver.models.dataservice.FileExport;
import software.uncharted.terarium.hmiserver.models.dataservice.notebooksession.NotebookSession;
import software.uncharted.terarium.hmiserver.repository.data.NotebookSessionRepository;
import software.uncharted.terarium.hmiserver.service.s3.S3ClientService;
import software.uncharted.terarium.hmiserver.utils.rebac.Schema;

@Service
public class NotebookSessionService extends TerariumAssetService<NotebookSession, NotebookSessionRepository> {

	public NotebookSessionService(
		final ObjectMapper objectMapper,
		final Config config,
		final ProjectService projectService,
		final ProjectAssetService projectAssetService,
		final NotebookSessionRepository repository,
		final S3ClientService s3ClientService
	) {
		super(
			objectMapper,
			config,
			projectService,
			projectAssetService,
			repository,
			s3ClientService,
			NotebookSession.class
		);
	}

	@Override
	protected String getAssetPath() {
		throw new UnsupportedOperationException("Notebook Sessions are not stored in S3");
	}

	@Override
	@Observed(name = "function_profile")
	public void copyAssetFiles(
		final NotebookSession newAsset,
		final NotebookSession oldAsset,
		final Schema.Permission hasWritePermission
	) throws IOException {
		throw new UnsupportedOperationException("Unimplemented");
	}

	@Override
	public Integer uploadFile(final UUID uuid, final String filename, final ContentType contentType, final byte[] data)
		throws IOException {
		throw new UnsupportedOperationException("Unimplemented");
	}

	@Override
	public Integer uploadFile(final UUID assetId, final String filename, final FileExport fileExport) throws IOException {
		throw new UnsupportedOperationException("Unimplemented");
	}
}
