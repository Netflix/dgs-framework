# Width of the target column in `make help`.
TARGET_MAX_CHAR_NUM ?= 30

# Available colors
RED    := $(shell tput -Txterm setaf 1)
GREEN  := $(shell tput -Txterm setaf 2)
YELLOW := $(shell tput -Txterm setaf 3)
BLUE   := $(shell tput -Txterm setaf 4)
WHITE  := $(shell tput -Txterm setaf 7)
RESET  := $(shell tput -Txterm sgr0)


define HELP_SCRIPT
  if ((/^```/ && $$p > 0) || /^```ascii/) { $$p++; next };
  print $$_ if ($$p == 1);
  if (/^([A-Za-z0-9_-]+[\/]?+[%]?)*:.*## (.*)/) {printf "${GREEN}%-${TARGET_MAX_CHAR_NUM}s${BLUE}: %s${RESET}\n", $$1, $$2, $$3 };
  if (/^###?/) { printf "\n" }
endef
export HELP_SCRIPT

define SHOW_IDENTITY
@[ -f ./.makefile.identity.inc ] && printf "${GREEN}" && cat ./.makefile.identity.inc; echo ''; echo '' || echo '' && printf "${RESET}"
endef
