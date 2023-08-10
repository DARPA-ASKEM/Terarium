package software.uncharted.terarium.hmiserver.services;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;

@QuarkusTest
public class StructuredLogTests {
	@Inject
	StructuredLog structuredLog;

	@Test
	public void testItThrowsWhenOddNumberOfArguments() {
		Assertions.assertThrows(RuntimeException.class, () -> structuredLog.log(StructuredLog.Type.EVENT, "adam", "single_element"));
	}

	@Test
	public void testItDoesntThrowWhenNoArguments() {
		Assertions.assertDoesNotThrow(() -> structuredLog.log(StructuredLog.Type.EVENT, "adam"));
	}

	@Test
	public void testItAllowsNullUser() {
		Assertions.assertDoesNotThrow(() -> structuredLog.log(StructuredLog.Type.EVENT, null, "foo", "bar"));
	}
}
