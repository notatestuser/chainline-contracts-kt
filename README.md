# chainline-contracts-kt

The smart contracts powering Chain Line are written in Kotlin for the [Neo](https://neo.org) blockchain.

## Project structure

* `./` The Kotlin project containing contract sources
* `./unit-tests` The .NET Core project containing VM based contract tests
* `./vendor/*` External dependencies

## Building the contracts

* Install the [.NET Core v1.1 SDK](https://github.com/dotnet/core/releases)
* Install JDK 8 and Maven
* Clone this repository and run `make`

Compiled contract avm files may be found in the root directory of the project.

> Note: You might have to put `org.neo.smartcontract.framework.jar` somewhere dotnet can find it (try your PATH or `/usr/local/share/dotnet`). You can build the jar by running `mvn install` in `vendor/neo-devpack-java`. It will be created in `target`.

## Running the tests

Run `make test` (the easy way) or

* Install Visual Studio
* Open the unit tests project in `./unit-tests/CLTests.sln`
* Run the tests within the IDE (on a Mac this is done through `View` > `Pads` > `Unit Tests` then click `Run All` in the side pane)

## Generating the docs

[Dokka](https://github.com/Kotlin/dokka) is used to generate the docs as an HTML site. Run `make docs` to generate the pages and then look in `target/dokka/chainline-contracts`.
