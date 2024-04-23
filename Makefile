version=3.81

PROJECT_DIR ?= .

.SECONDEXPANSION:

.PHONY: all
all:
	@echo "make <cmd>"
	@echo ""
	@echo "Commands:"
	@echo "  clean                    - remove bin, dist and node_modules directories"
	@echo "  images                   - build docker images of all targets"
	@echo "  image-<target>           - build docker image of a specific target - see below for list of targets"



## Targets
TARGETS :=



TARGETS += hmi-server
clean-hmi-server: clean-hmi-server-base
	rm -rf $(PROJECT_DIR)/packages/server/docker/build

image-hmi-server: clean-hmi-server
	./gradlew :packages:server:build -x test
	mv $(PROJECT_DIR)/packages/server/build $(PROJECT_DIR)/packages/server/docker/build

TARGETS += hmi-client
clean-hmi-client:
	rm -rf $(PROJECT_DIR)/packages/client/graph-scaffolder/build
	rm -rf $(PROJECT_DIR)/packages/client/graph-scaffolder/dist
	rm -rf $(PROJECT_DIR)/packages/client/hmi-client/dist
	rm -rf $(PROJECT_DIR)/packages/client/hmi-client/docker/dist

image-hmi-client: clean-hmi-client yarn-install
	yarn workspace graph-scaffolder tsc --build
	yarn workspace hmi-client build
	mv $(PROJECT_DIR)/packages/client/hmi-client/dist $(PROJECT_DIR)/packages/client/hmi-client/docker/dist

TARGETS += db-migration
clean-db-migration: clean-db-migration-base
	rm -rf $(PROJECT_DIR)/packages/db-migration/docker/build

image-db-migration: clean-db-migration
	./gradlew :packages:db-migration:build -x test
	mv $(PROJECT_DIR)/packages/db-migration/build $(PROJECT_DIR)/packages/db-migration/docker/build

TARGETS += gollm-taskrunner
clean-gollm-taskrunner: clean-gollm-taskrunner-base
	rm -rf $(PROJECT_DIR)/packages/taskrunner/docker/build

image-gollm-taskrunner: clean-gollm-taskrunner
	./gradlew :packages:taskrunner:build -x test
	mv $(PROJECT_DIR)/packages/taskrunner/build $(PROJECT_DIR)/packages/taskrunner/docker/build

TARGETS += mira-taskrunner
clean-mira-taskrunner: clean-mira-taskrunner-base
	rm -rf $(PROJECT_DIR)/packages/taskrunner/docker/build

image-mira-taskrunner: clean-mira-taskrunner
	./gradlew :packages:taskrunner:build -x test
	mv $(PROJECT_DIR)/packages/taskrunner/build $(PROJECT_DIR)/packages/taskrunner/docker/build

TARGETS += funman-taskrunner
clean-funman-taskrunner: clean-funman-taskrunner-base
	rm -rf $(PROJECT_DIR)/packages/taskrunner/docker/build

image-funman-taskrunner: clean-funman-taskrunner
	./gradlew :packages:taskrunner:build -x test
	mv $(PROJECT_DIR)/packages/taskrunner/build $(PROJECT_DIR)/packages/taskrunner/docker/build




## Clean
.PHONY: clean
clean: $(TARGETS:%=clean-%)
	rm -rf $(PROJECT_DIR)/node_modules

.PHONY: clean-hmi-server-base
clean-hmi-server-base:
	./gradlew :packages:server:clean

.PHONY: clean-db-migration-base
clean-db-migration-base:
	./gradlew :packages:db-migration:clean

.PHONY: clean-gollm-taskrunner-base
clean-gollm-taskrunner-base:
	./gradlew :packages:taskrunner:clean

.PHONY: clean-mira-taskrunner-base
clean-mira-taskrunner-base:
	./gradlew :packages:taskrunner:clean

.PHONY: clean-funman-taskrunner-base
clean-funman-taskrunner-base:
	./gradlew :packages:taskrunner:clean



## Images
.PHONY: images
images: $(TARGETS:%=image-%)



## Utilities
yarn-install:
	yarn install
