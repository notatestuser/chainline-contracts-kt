build: update-submodules build-classes build-neoj build-avms

build-classes:
	mvn clean install

build-neoj:
	dotnet build -c Release ./vendor/neo-compiler/neoj/neoj.csproj

build-avms:
	find ./target/classes/chainline -name "*.class" | xargs -n 1 dotnet ./vendor/neo-compiler/neoj/bin/Release/netcoreapp1.1/neoj.dll

test: submodules
	mvn test

update-submodules:
	git submodule update --recursive .

.PHONY: build build-classes build-neoj build-avms test update-submodules
