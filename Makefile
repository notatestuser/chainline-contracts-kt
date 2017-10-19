build: build-neoj build-classes

build-test: build-classes test

watch: build-neoj install-chokidar
	chokidar src/**/*.kt -c "make build-test"

watch-sources: build-neoj install-chokidar
	chokidar src/**/*.kt -m "make build-classes"

watch-classes: build-neoj install-chokidar
	chokidar target/**/*.class -m "make build-avms && make test"

build-classes:
	mvn clean install

build-neoj: init-submodules
	dotnet build -c Release ./vendor/neo-compiler/neoj/neoj.csproj

build-avms:
	rm -f ./*.avm && \
	find ./target/classes/chainline -name "*.class" | xargs -n 1 dotnet ./vendor/neo-compiler/neoj/bin/Release/netcoreapp1.1/neoj.dll

test: init-submodules
	dotnet test unit-tests

init-submodules:
	git submodule update --init

install-chokidar:
	npm i -g chokidar-cli
