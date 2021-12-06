[![grantshares Actions Status](https://github.com/AxLabs/grantshares-contracts/workflows/grantshares-ci-cd/badge.svg)](https://github.com/AxLabs/grantshares-contracts/actions)

# GrantShares: Smart Contracts

This git repo contains the smart contracts for the GrantSharesDAO.

If you want to know more about GrantShares, please refer to the root repository:
* [https://github.com/AxLabs/grantshares](https://github.com/AxLabs/grantshares)

## Development

Clone this repo:

```shell
git clone https://github.com/AxLabs/grantshares-contracts.git
```

Compiling the smart contract (the .nef, .manifest, and .nefdbgnfo output files are at `./build/neow3j`):

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