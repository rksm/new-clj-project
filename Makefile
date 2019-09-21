build:
	$(MAKE) clean && $(MAKE) bin && bin/run -l

# -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
# repl / dev

nrepl:
	clojure -R:dev:nrepl:test -C:nrepl:test -m rksm-project-templates.nrepl

# -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
# graal

AOT := target/classes

$(AOT): src/rksm_project_templates/main.clj
	mkdir -p $(AOT)
	clojure -A:aot

RESOURCE_CONFIG := target/graal-resource-config.json

RESOURCES := $(wildcard resources/*)

$(RESOURCE_CONFIG): $(RESOURCES)
	clojure -A:graal-prep

BIN := bin/run

$(BIN): $(AOT) $(RESOURCE_CONFIG)
	mkdir -p bin
	/home/robert/install/graalvm-ce-19.2.0/bin/native-image \
		--report-unsupported-elements-at-runtime \
		--verbose \
		--no-server \
		--initialize-at-build-time \
		-cp $(shell clojure -C:aot -Spath) \
		--no-fallback \
		--enable-http --enable-https --allow-incomplete-classpath \
		-H:+ReportExceptionStackTraces \
		-H:ResourceConfigurationFiles=$(RESOURCE_CONFIG) \
		rksm_project_templates.main \
		$(BIN)
	cp -r resources $(dir $(BIN))/

bin: $(BIN)

# -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

clean:
	rm -rf target .cpcache bin

.PHONY: nrepl clean bin build
