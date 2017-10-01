build: init-submodules build-neoj build-classes

build-classes:
	mvn clean install

build-neoj:
	dotnet build -c Release ./vendor/neo-compiler/neoj/neoj.csproj

build-avms:
	rm -f ./*.avm && \
	find ./target/classes/chainline -name "*.class" | xargs -n 1 dotnet ./vendor/neo-compiler/neoj/bin/Release/netcoreapp1.1/neoj.dll

test: submodules
	mvn test

init-submodules:
	git submodule update --init

.PHONY: build build-classes build-neoj build-avms test init-submodules
