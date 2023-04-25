include .env

WORKING_DIR := $(shell pwd)
MANIPULATOR_DIR := $(WORKING_DIR)
MANIPULATOR_OUT_DIR := $(WORKING_DIR)/manipulated
PACKAGE_NAME_AS_DIR := $(subst .,/,$(PACKAGE_NAME))
AGENT_DIR := ./src/main/java/$(PACKAGE_NAME_AS_DIR)
DOTFILE := ./callgraph.dot

CLASS_PATHS := $(MANIPULATOR_DIR)/target/log_insertion-1.0-jar-with-dependencies.jar:$(SOURCE_DIR)/target/classes:./target/classes kr.ac.knu.isslab.MainDriver
SOOT_OPTIONS := -w -process-dir $(SOURCE_DIR)/target/classes -keep-line-number -d $(MANIPULATOR_OUT_DIR)

objects = $(WORKING_DIR) $(MANIPULATOR_DIR) $(MANIPULATOR_OUT_DIR) $(AGENT_DIR)

.PHONY: logo
logo:
	@echo "\n _                  ___                     _   _              \n| |    ___   __ _  |_ _|_ __  ___  ___ _ __| |_(_) ___  _ __   \n| |   / _ \\ / _\` |  | || '_ \\/ __|/ _ \\ '__| __| |/ _ \\| '_ \\  \n| |__| (_) | (_| |  | || | | \\__ \\  __/ |  | |_| | (_) | | | | \n|_____\\___/ \\__, | |___|_| |_|___/\\___|_|   \\__|_|\\___/|_| |_| \n            |___/\n"


.PHONY: info
info:
	@echo "Kyungpool National University Intelligent Software Systems Lab"
	@echo "Author: Juyong Shin, Young-Woo Kwon"
	@echo ""

check-java::
ifneq ($(shell java -version 2>&1 | grep "1.8" > /dev/null; printf $$?),0)
	@echo "No java or version not match"; exit 1;
endif

check-mvn::
ifneq ($(shell mvn -version 2>&1 | grep "Maven" > /dev/null; printf $$?),0)
	@echo "No maven or version not match"; exit 1;
endif

dependencies: check-java check-mvn
	@echo "Dependencies:"
	@java -version; echo ""
	@mvn -version; echo ""

.PHONY: envs
envs:
	@echo "Environment variables:"
	@echo "WORKING_DIR: $(WORKING_DIR)"
	@echo "SOURCE_DIR: $(SOURCE_DIR)"
	@echo "MANIPULATOR_DIR: $(MANIPULATOR_DIR)"
	@echo "MANIPULATOR_OUT_DIR: $(MANIPULATOR_OUT_DIR)"
	@echo "CLASS_PATHS: $(CLASS_PATHS)"
	@echo "AGENT_DIR: $(AGENT_DIR)"
	@echo ""

.PHONY: header
header: logo info dependencies envs

check-result-dir::
	@if [ ! -d "$(MANIPULATOR_OUT_DIR)" ]; then \
		echo "Create directory: $(MANIPULATOR_OUT_DIR)"; \
		mkdir -p $(MANIPULATOR_OUT_DIR); \
	fi

config: header check-result-dir
	@echo "Configuration:"
	@bash resources/configure.sh $(PACKAGE_NAME) $(WORKING_DIR)
	@echo ""

pre-build: clean config $(objects)
	@echo "Pre-Build:"
	mvn -f $(SOURCE_DIR) compile; \
	mvn -f $(MANIPULATOR_DIR) package -Dmaven.test.skip=true

build: pre-build
	@echo "Build:"
	java -Xmx4g -cp $(CLASS_PATHS) $(SOOT_OPTIONS)
	@echo ""

build-jimple: pre-build
	@echo "Build-Jimple:"
	java -Xmx4g -cp $(CLASS_PATHS) $(SOOT_OPTIONS) -f J
	@echo ""

package: build
	@echo "Package:"
	@cp $(MANIPULATOR_DIR)/target/classes/$(PACKAGE_NAME_AS_DIR)/MyAgent*.class $(MANIPULATOR_OUT_DIR)/$(PACKAGE_NAME_AS_DIR); \
	cp -r $(MANIPULATOR_OUT_DIR)/com $(SOURCE_DIR)/target/classes; mvn -f $(SOURCE_DIR) package -Dmaven.compile.skip=true;
	@echo ""

.PHONY: clean
clean:
	@echo "Clean these directories: $(objects)"
	@if [ -d "${MANIPULATOR_OUT_DIR}" ]; then \
		rm -rf ${MANIPULATOR_OUT_DIR}; \
	fi
	@if [ -d "${AGENT_DIR}" ]; then \
		rm -rf ${AGENT_DIR}; \
	fi
	@if [ -d "${SOURCE_DIR}" ]; then \
		mvn -f ${SOURCE_DIR} clean; \
	fi
	@if [ -d "${MANIPULATOR_DIR}" ]; then \
		mvn -f ${MANIPULATOR_DIR} clean; \
	fi
	@if [ -f "${DOTFILE}" ]; then \
    	rm $(DOTFILE)*; \
    fi
	@echo ""
