.SILENT:

INSTALL_PREFIX?="$(HOME)"
JAR_NAME="dshell.jar"
BIN_NAME="dshell"
TOOLS_DIR="./tools"

PARSER_OUTDIR="./gensrc/dshell/internal/parser"

all: build

build: preprocess
	ant

preprocess:
	python ./tools/gen-array.py ./src/dshell/lang/GenericArray.java
	java -jar ./lib/antlr-4.3-complete.jar ./dshellLexer.g4 ./dshellParser.g4 -o ${PARSER_OUTDIR} -no-listener -no-visitor -encoding UTF-8

clean:
	rm -rf ./gensrc
	rm -rf ./generated-array
	ant clean

clean-launcher:
	make -C $(TOOLS_DIR)/launcher clean

install: clean-launcher
	echo "install dshell to $(INSTALL_PREFIX)/bin"
	install -d $(INSTALL_PREFIX)/bin
	make -C $(TOOLS_DIR)/launcher JAR_PREFIX=$(INSTALL_PREFIX)/bin
	cp -f $(JAR_NAME) $(INSTALL_PREFIX)/bin/
	install -m 775 $(TOOLS_DIR)/launcher/$(BIN_NAME) $(INSTALL_PREFIX)/bin/

test:
	TEST_DIR=./test dshell ./test/run_test.ds

.PHONY: all build clean install test
