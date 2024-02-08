package software.uncharted.terarium.hmiserver.models.dataservice.multiphysics;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import software.uncharted.terarium.hmiserver.annotations.TSModel;
import software.uncharted.terarium.hmiserver.models.dataservice.TerariumAsset;

import java.io.Serializable;

@EqualsAndHashCode(callSuper = true)
@Data
@Accessors(chain = true)
@TSModel
public class DecapodesContext extends TerariumAsset implements Serializable {
	private ContextHeader header;
	private Context context;
}
