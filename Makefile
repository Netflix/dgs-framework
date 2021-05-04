.PHONY: help all
.DEFAULT_GOAL := help

SHELL = /bin/sh

## Gradle
GW = ./gradlew
GFLAGS ?=
GW_CMD = $(GW) $(GFLAGS)

# GW_OPT_DISABLE_LOCAL=-x autoLintGradle
GW_OPT_DISABLE_LOCAL=


format: ## Formats source code
	$(GW_CMD) ktlintFormat


publish-local: ## Clans, bulds, and publishes the Codegen artifacts to mavenLocal, as a SNAPSHOT.
	$(GW_CMD) $(GW_OPT_DISABLE_LOCAL) clean build publishToMavenLocal


test-examples_py: ## Modify the examples to use the latest Codegen SNAPSHOT, publishes the snapshot locally, and builds the examples.
	scripts/test-examples.py -v -g --path=build/examples

test-examples: ## Modify the examples to use the latest Codegen SNAPSHOT, publishes the snapshot locally, and builds the examples.
	$(MAKE) publish-local
	$(MAKE) test-examples_py

install-py-libs: ## Installs the Python Modules required by the scripts.
	 pip3 install -r scripts/requirements.txt


all: test-examples ## Cleans, checks/tests, publishes the plugin locally and runs the examples.


help:
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-30s\033[0m %s\n", $$1, $$2}'
