package software.uncharted.terarium.esingest;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;

import lombok.extern.slf4j.Slf4j;
import software.uncharted.terarium.esingest.configuration.ElasticsearchConfiguration;
import software.uncharted.terarium.esingest.models.input.covid.CovidDocument;
import software.uncharted.terarium.esingest.models.input.covid.CovidEmbedding;
import software.uncharted.terarium.esingest.models.output.Document;
import software.uncharted.terarium.esingest.models.output.Document.Paragraph;
import software.uncharted.terarium.esingest.models.output.Embedding;
import software.uncharted.terarium.esingest.service.ElasticIngestParams;
import software.uncharted.terarium.esingest.service.ElasticIngestService;

@SpringBootApplication
@Slf4j
@PropertySource("classpath:application.properties")
public class ElasticIngestApplication {

	@Autowired
	ElasticsearchConfiguration esConfig;

	@Autowired
	ElasticIngestService esIngestService;

	@Autowired
	ApplicationContext context;

	public static void main(String[] args) {
		SpringApplication.run(ElasticIngestApplication.class, args);
	}

	@Bean
	public ApplicationRunner applicationRunner() {
		return args -> {
			try {
				ElasticIngestParams params = new ElasticIngestParams();
				params.setInputDir("/home/kbirk/Downloads/covid");
				params.setOutputIndex(esConfig.getCovidIndex());

				List<String> errs = esIngestService.ingestData(params,
						(CovidDocument input) -> {

							Document doc = new Document();
							doc.setId(input.getId());
							doc.setTitle(input.getSource().getTitle());
							doc.setFullText(input.getSource().getBody());

							return doc;
						},
						(CovidEmbedding input) -> {

							Paragraph paragraph = new Paragraph();
							paragraph.setParagraphId(input.getEmbeddingChunkId().toString());
							paragraph.setSpans(input.getSpans());
							paragraph.setVector(input.getEmbedding());

							Embedding<Paragraph> embedding = new Embedding<>();
							embedding.setId(input.getId());
							embedding.setEmbedding(paragraph);

							return embedding;

						}, CovidDocument.class, CovidEmbedding.class);

				for (String err : errs) {
					log.error(err);
				}

				// Shut down the application gracefully
				SpringApplication.exit(context, () -> 0);
			} catch (Exception e) {
				e.printStackTrace();

				SpringApplication.exit(context, () -> 1);
			}

		};
	}
}
