[![grantshares Actions Status](https://github.com/AxLabs/grantshares-contracts/workflows/grantshares-ci-cd/badge.svg)](https://github.com/AxLabs/grantshares-contracts/actions)

# GrantShares: Smart Contracts

This git repo contains the smart contracts for the GrantSharesDAO.

## Context

The GrantShares Program is a discretionary grants program governed by the GrantSharesDAO, where
funds would be primarily provided by the Neo Foundation and partner initiatives.

GrantSharesDAO is composed by GrantShares 'Council Members' (or, in short, "GrantSharesDAO Members"),
which are initially appointed by the Neo Foundation. The GrantSharesDAO Members are a small group
of individuals/entities aiming to increase Neo's impact and reach through community activities.

The DAO Members analyze project proposals submitted to the GrantSharesDAO, with the goal to benefit
the growth and development of the Neo ecosystem. Each DAO Member may vote to approve or reject a proposal.

## Development

Clone this repo:

```shell
git clone https://github.com/AxLabs/grantshares-contracts.git
```

Compiling the smart contract (the .nef and .manifest output files are at `./build/neow3j`):

```shell
./gradlew clean neow3jCompile
```

Running the tests:

```shell
./gradlew clean test
```

## Documentation

Documents about proposals, flow, etc., are in the official documentation page for GrantShares.

## Maintainers

[AxLabs](https://axlabs.com) develops and maintains the GrantSharesDAO, with
the support from the whole community.

## License

Licensed under [Apache2](http://www.apache.org/licenses/LICENSE-2.0).

```
   Copyright 2021 AxLabs (axlabs.com)

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
```