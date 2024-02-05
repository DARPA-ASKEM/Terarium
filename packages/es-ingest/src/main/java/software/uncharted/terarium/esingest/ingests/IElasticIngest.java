package software.uncharted.terarium.esingest.ingests;

import software.uncharted.terarium.esingest.models.input.IInputDocument;
import software.uncharted.terarium.esingest.models.input.IInputEmbeddingChunk;
import software.uncharted.terarium.esingest.models.output.IOutputDocument;
import software.uncharted.terarium.esingest.models.output.IOutputEmbeddingChunk;

public interface IElasticIngest<DocInputType extends IInputDocument, DocOutputType extends IOutputDocument, EmbeddingInputChunkType extends IInputEmbeddingChunk, EmbeddingOutputChunkType extends IOutputEmbeddingChunk> {

	public abstract DocOutputType processDocument(DocInputType input);

	public abstract EmbeddingOutputChunkType processEmbedding(EmbeddingInputChunkType input);

	public abstract DocInputType deserializeDocument(String line);

	public abstract EmbeddingInputChunkType deserializeEmbedding(String line);

}
