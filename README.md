![Chain Line](http://d.pr/f/Oo2c4f+)

**Get anything anywhere, powered by the blockchain.** Chain Line is a peer-to-peer shipping network that gets products and valuable items to their destinations with near-zero risk.

It works by moving an item's cost between peers as it travels through the system. A central "hub" contract controls "reserved funds" in Chain Line [smart wallets](https://github.com/notatestuser/chainline-contracts-kt/wiki/Smart-Wallet) running custom verification scripts. Chain Line features a user reputation system and relies on no external systems, operating entirely on the blockchain.

#### Learn more:&nbsp;&nbsp; [Wiki](https://github.com/notatestuser/chainline-contracts-kt/wiki) &nbsp; [User Guide](https://github.com/notatestuser/chainline-webapp/wiki/Web-App-User-Guide) &nbsp; [Intro Video](https://f001.backblazeb2.com/file/chainline-assets/explainer.mp4)
---

# Chain Line Contracts

The smart contracts powering Chain Line run on the [NEO](https://neo.org) blockchain and were written in Kotlin.

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

A snapshot of the docs is accessible [via GitHub Pages](https://notatestuser.github.io/chainline-contracts-kt).
