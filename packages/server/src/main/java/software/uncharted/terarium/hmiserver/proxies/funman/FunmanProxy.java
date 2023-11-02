package software.uncharted.terarium.hmiserver.proxies.funman;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "funman-api", url = "${funman-service.url}", path="/queries")
public interface FunmanProxy {

    @GetMapping("/{queryId}/halt")
    ResponseEntity<JsonNode> halt(@PathVariable("queryId") String queryId);
   
    @GetMapping("/{queryId}")
    ResponseEntity<JsonNode> getQueries(@PathVariable("queryId") String queryId);

    @PostMapping
    ResponseEntity<JsonNode> postQueries(@RequestBody JsonNode requestBody);
}