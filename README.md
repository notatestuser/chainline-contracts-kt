# chainline-contracts-kt

The smart contracts powering Chain Line are written in Kotlin for the [Neo](https://neo.org) blockchain.

## Project structure

* `./` The Kotlin project containing contract sources
* `./unit-tests` The .NET Core project containing VM based contract tests
* `./vendor/*` External dependencies

## Building the contracts

* Install the [.NET Core v1.1 SDK](https://github.com/dotnet/core/releases)
* Install Maven
* Clone this repository and run `make`

Compiled contract avm files may be found in the root directory of the project.

## Running the tests

* Install Visual Studio
* Open the unit tests project in `./unit-tests/CLTests.sln`
* Run the tests within the IDE (on a Mac this is done through `View` > `Pads` > `Unit Tests` then click `Run All` in the side pane)

