package software.uncharted.terarium.hmiserver;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import software.uncharted.terarium.hmiserver.utils.rebac.Schema;

@AutoConfigureMockMvc
@SpringBootTest
@Transactional
@ActiveProfiles({"local", "test"})
public class TerariumApplicationTests {
	@Autowired
	private WebApplicationContext context;

	@Autowired
	public MockMvc mockMvc;

	@BeforeEach
	public void beforeEach() {
		mockMvc = MockMvcBuilders.webAppContextSetup(this.context)
				.apply(springSecurity())
				.build();
	}

	/** To allow the tests to run easily grant write permissions (wihtout chacking against the ReBAC service) */
	public Schema.Permission ASSUME_WRITE_PERMISSION = Schema.Permission.WRITE;
	/** To allow calls to Asset and Project Controllers a projectId is required */
	public UUID PROJECT_ID = UUID.randomUUID();
}
