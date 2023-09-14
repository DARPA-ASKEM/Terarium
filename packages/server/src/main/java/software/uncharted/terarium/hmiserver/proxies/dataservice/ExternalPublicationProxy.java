package software.uncharted.terarium.hmiserver.proxies.dataservice;

import org.springframework.cloud.openfeign.FeignClient;
import software.uncharted.terarium.hmiserver.models.dataservice.DocumentAsset;


@FeignClient(name = "externalPublications", url = "${terarium.dataservice.url}", path = "/external/publications")

public interface ExternalPublicationProxy extends TDSProxy<DocumentAsset> {

}
