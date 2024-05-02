package software.uncharted.terarium.hmiserver.repository.data;

import java.util.UUID;

import org.springframework.stereotype.Repository;

import software.uncharted.terarium.hmiserver.models.dataservice.dataset.Dataset;
import software.uncharted.terarium.hmiserver.repository.PSCrudSoftDeleteRepository;

@Repository
public interface DatasetRepository extends PSCrudSoftDeleteRepository<Dataset, UUID> {
}
