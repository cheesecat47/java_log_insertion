include .env

WORKING_DIR := $(shell pwd)
MANIPULATOR_DIR := $(WORKING_DIR)/java_log_insertion
MANIPULATOR_OUT_DIR := $(WORKING_DIR)/manipulated

objects = $(WORKING_DIR) $(MANIPULATOR_DIR) $(MANIPULATOR_OUT_DIR)

.PHONY: logo
logo:
	@echo "\n _                  ___                     _   _              \n| |    ___   __ _  |_ _|_ __  ___  ___ _ __| |_(_) ___  _ __   \n| |   / _ \\ / _\` |  | || '_ \\/ __|/ _ \\ '__| __| |/ _ \\| '_ \\  \n| |__| (_) | (_| |  | || | | \\__ \\  __/ |  | |_| | (_) | | | | \n|_____\\___/ \\__, | |___|_| |_|___/\\___|_|   \\__|_|\\___/|_| |_| \n            |___/\n"


.PHONY: info
info:
	@echo "Kyungpool National University Intelligent Software Systems Lab\nAuthor: Juyong Shin, Young-Woo Kwon\n"

.PHONY: dependencies
dependencies:
	@echo "Dependencies:"
	@java -version
	@mvn -version
	@echo "\n"

.PHONY: envs
envs:
	@echo "Environment variables:"
	@echo "WORKING_DIR: $(WORKING_DIR)"
	@echo "SOURCE_DIR: $(SOURCE_DIR)"
	@echo "MANIPULATOR_DIR: $(MANIPULATOR_DIR)"
	@echo "MANIPULATOR_OUT_DIR: $(MANIPULATOR_OUT_DIR)"
	@echo "\n"

.PHONY: header
header: logo info dependencies envs

check-java::
ifneq ($(shell java -version 2>&1 | grep "1.8" > /dev/null; printf $$?),0)
	@echo "No java or version not match"; exit 1;
endif

check-mvn::
ifneq ($(shell mvn -version 2>&1 | grep "Maven" > /dev/null; printf $$?),0)
	@echo "No maven or version not match"; exit 1;
endif

check-result-dir::
	@if [ ! -d "$(WORKING_DIR)/manipulated" ]; then \
		echo -e "Create directory: $(MANIPULATOR_OUT_DIR)\n"; \
		mkdir -p $(MANIPULATOR_OUT_DIR); \
	fi

config: header check-java check-mvn check-result-dir
	@echo "Configuration:"

build: config $(objects)
	@echo "Build:"
	@echo "$(objects)"
	mvn -f $(SOURCE_DIR) clean compile
	mvn -f $(MANIPULATOR_DIR) clean package -Dmaven.test.skip=true
	java -Xmx4g -cp $(MANIPULATOR_DIR)/target/java_log_insertion-1.0-SNAPSHOT-jar-with-dependencies.jar:$(SOURCE_DIR)/target/classes kr.ac.knu.isslab.MainDriver -w -process-dir $(SOURCE_DIR)/target/classes -keep-line-number -d ${MANIPULATOR_OUT_DIR} | tee soot_process.log

package: build
	@echo "Package:"
	@cp $(MANIPULATOR_DIR)/target/classes/com/finedigital/MyAgent*.class $(MANIPULATOR_OUT_DIR)/com/finedigital; \
	cp -r $(MANIPULATOR_OUT_DIR)/com $(SOURCE_DIR)/target/classes; mvn -f $(SOURCE_DIR) package -Dmaven.compile.skip=true;

.PHONY: clean
clean: config $(objects)
	@echo "Clean these directories: $(objects)"
	rm -rf ${MANIPULATOR_OUT_DIR}
	mvn -f ${SOURCE_DIR} clean
	mvn -f ${MANIPULATOR_DIR} clean
