include .makefile.inc

.PHONY: help all jdk format publish-local test-examples test-examples_py install-py-libs
.DEFAULT_GOAL := help

SHELL = /bin/sh

# Gradle
GW = ./gradlew
GFLAGS ?=

# Byte Buddy (used by mockk and Mockito) writes why a failed agent attach happened to this file, under the build/ directory. 
# Tests also load the agent with -javaagent (see build.gradle.kts), so this is for diagnosis.
BB_ATTACH_DUMP ?= $(CURDIR)/build/bytebuddy/byte-buddy-attach.log
GW_ENV = mkdir -p $(dir $(BB_ATTACH_DUMP)) && JAVA_TOOL_OPTIONS="$$JAVA_TOOL_OPTIONS -Dnet.bytebuddy.agent.attacher.dump=$(BB_ATTACH_DUMP)"
GW_CMD = $(GW_ENV) $(GW) $(GFLAGS)

# JDK
# Every Gradle invocation runs on a single JDK major version, JDK_VERSION.
# The examples pin a Gradle version that cannot run on the newest JDKs, and this keeps the framework build consistent with them.
# To bump it, update `java=` in .sdkmanrc, or override per run: `make JDK_VERSION=21 all`
# ----
# Resolution order for JDK_HOME:
# 	1. the override,
# 	2. the active JAVA_HOME if it is that major version (e.g. after `sdk env`), `java_home -v`,
# 	3. then the newest matching SDKMAN JDK.
#
JDK_VERSION ?= $(or $(shell sed -n 's/^java=\([0-9]*\)[.].*/\1/p' .sdkmanrc 2>/dev/null),17)
JDK_HOME ?= $(shell \
	if [ -x "$$JAVA_HOME/bin/java" ] && "$$JAVA_HOME/bin/java" -version 2>&1 | grep -q 'version "$(JDK_VERSION)[."]'; then echo "$$JAVA_HOME"; \
	elif h=$$(/usr/libexec/java_home -v $(JDK_VERSION) 2>/dev/null); then echo "$$h"; \
	else ls -d $(HOME)/.sdkman/candidates/java/$(JDK_VERSION).* 2>/dev/null | sort -V | tail -1; fi)
ifneq ($(JDK_HOME),)
export JAVA_HOME := $(JDK_HOME)
export PATH := $(JDK_HOME)/bin:$(PATH)
endif

# Python
# Requires uv (https://docs.astral.sh/uv/), e.g. `brew install uv`.
UV ?= uv
VENV = .venv
VENV_PY = $(VENV)/bin/python

# GW_OPT_DISABLE_LOCAL=-x autoLintGradle
GW_OPT_DISABLE_LOCAL=

jdk: # Fails unless a JDK of version JDK_VERSION was found, see JDK_HOME.
	@test -x "$(JDK_HOME)/bin/java" || \
		{ echo "JDK $(JDK_VERSION) not found. Run 'sdk env' or set JDK_HOME to a JDK $(JDK_VERSION) home."; exit 1; }

format: jdk ## Formats source code
	$(GW_CMD) ktlintFormat

publish-local: jdk ## Clans, bulds, and publishes the Codegen artifacts to mavenLocal, as a SNAPSHOT.
	$(GW_CMD) $(GW_OPT_DISABLE_LOCAL) clean build publishToMavenLocal

test-examples_py: jdk $(VENV)/.installed # Modify the examples to use the latest Codegen SNAPSHOT, publishes the snapshot locally, and builds the examples.
	@# The example builds run their own tests (Mockito), so their JVMs get the Byte Buddy agent at startup too.
	@# JAVA_TOOL_OPTIONS is inherited by the Gradle daemon and the forked test JVMs.
	@agent=$$($(GW) -q :graphql-dgs:byteBuddyAgentPath | tail -1) && test -f "$$agent" \
		&& mkdir -p $(dir $(BB_ATTACH_DUMP)) \
		&& JAVA_TOOL_OPTIONS="$$JAVA_TOOL_OPTIONS -javaagent:$$agent -Dnet.bytebuddy.agent.attacher.dump=$(BB_ATTACH_DUMP)" \
			$(VENV_PY) scripts/test-examples.py -v -g -k --path=build/examples

test-examples: ## Modify the examples to use the latest Codegen SNAPSHOT, publishes the snapshot locally, and builds the examples.
	$(MAKE) publish-local
	$(MAKE) test-examples_py

install-py-libs: $(VENV)/.installed # Creates the .venv virtual environment and installs the Python modules required by the scripts.

$(VENV)/.installed: scripts/requirements.txt
	@command -v $(UV) >/dev/null || { echo "uv not found. Install it, e.g. 'brew install uv'."; exit 1; }
	$(UV) venv $(VENV)
	$(UV) pip install --python $(VENV_PY) -r scripts/requirements.txt
	touch $@

all: format test-examples ## Cleans, checks/tests, publishes the plugin locally and runs the examples.

help: ## List all available commands.
	${SHOW_IDENTITY}
	@echo 'Usage:'
	@echo '${YELLOW}make${RESET} ${GREEN}<target>${RESET}'
	@echo 'or pass any ${RED}Gradle task${RESET}, e.g.'
	@echo '${YELLOW}make${RESET} ${RED}check${RESET}'
	@echo ''
	@echo 'Targets:'
	@perl -ne "$${HELP_SCRIPT}" $(MAKEFILE_LIST)

# Prevents the catch-all rule below from trying to "remake" this file and the included ones through Gradle.
Makefile .makefile.inc .makefile.identity.inc: ;

# The JDK check is part of the recipe, not a prerequisite: a prerequisite makes make try to "remake" any existing
# file that has no rule (e.g. scripts/requirements.txt) by running this rule on it.
%: ## Runs a given gradle task. e.g. check
	@$(MAKE) --no-print-directory jdk
	$(GW_CMD) $(@F)
