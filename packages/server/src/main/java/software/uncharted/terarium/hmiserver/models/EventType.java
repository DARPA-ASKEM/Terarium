package software.uncharted.terarium.hmiserver.models;

import lombok.Getter;
import software.uncharted.terarium.hmiserver.annotations.TSModel;

@TSModel
public enum EventType {
	SEARCH(true),
	ROUTE_TIMING(true),
	PROXY_TIMING(true),
	ADD_RESOURCES_TO_PROJECT(true),
	EXTRACT_MODEL(true),
	PERSIST_MODEL(true),
	TRANSFORM_PROMPT(true),
	ADD_CODE_CELL(true),
	RUN_SIMULATION(true),
	RUN_CALIBRATE(true),
	GITHUB_IMPORT(true);

	EventType(boolean persistent) {
		this.persistent = persistent;
	}

	EventType() {
		this.persistent = false;
	}

	@Getter
	final boolean persistent;
}
