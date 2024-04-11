package software.uncharted.terarium.hmiserver.models.documentservice;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class RelatedDocument {

  /** The related document */
  private Document bibjson;

  private Number score;

  public Document getDocument() {
    return this.bibjson;
  }
}
