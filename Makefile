build: build-neoj build-classes build-avms

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

