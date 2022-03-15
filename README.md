[![grantshares Actions Status](https://github.com/AxLabs/grantshares-contracts/workflows/grantshares-ci-cd/badge.svg)](https://github.com/AxLabs/grantshares-contracts/actions)

# GrantShares: Smart Contracts

This git repo contains the smart contracts for the GrantShares program.

If you want to know more about GrantShares, please refer to the root repository:

* [https://github.com/AxLabs/grantshares](https://github.com/AxLabs/grantshares)

## Development

Clone this repo:

```shell
git clone https://github.com/AxLabs/grantshares-contracts.git
```

Run the tests:

```shell
./gradlew test
```

## Deployment

GrantShares consists of two smart contracts. Both need to be configured at deploy time.

### GrantSharesGov Deploy Configuration

For the GrantSharesGov contract the following values have to be set. Names of parameters are given in brackets.

- Review phase length ("review_len")
- Voting phase length ("voting_len")
- Timelock phase length ("timelock_len")
- Minimum acceptance rate of a proposal ("min_accept_rate")
- Minimum quorum of a proposal ("min_quorum")
- Members multi-sig address signing threshold ("threshold")
- The initial GrantShares members

At deploy time all of these things have to be passed as one parameter. This is the expected structure:

```json
{
  "type": "Array",
  "value": [
    {
      "type": "Array",
      "value": [
        {
          "type": "PublicKey",
          "value": "<member1 pubkey>"
        },
        {
          "type": "PublicKey",
          "value": "<member2 pubkey>"
        },
        ...
      ]
    },
    {
      "type": "Array",
      "value": [
        {
          "type": "String",
          "value": "<param1 name>"
        },
        {
          "type": "Integer",
          "value": "<param1 value>"
        },
        {
          "type": "String",
          "value": "<param2 name>"
        },
        {
          "type": "Integer",
          "value": "<param2 value>"
        },
        ...
      ]
    }
  ]
}
```

### GrantSharesTreasury Deploy Configuration

For the treasury contract the following values have to be set.

- Contract owner, which is the GrantSharesGov contract hash
- The initial GrantShares funders
- Whitelisted tokens and their maximum funding amounts
- Funders multi-sig address signing threshold

At deploy time all of these things have to be passed as one parameter. This is the expected structure:

```json
{
  "type": "Array",
  "value": [
    {
      "type": "Hash160",
      "value": "<GrantSharesGov contract hash>"
    },
    {
      "type": "Map",
      "value": [
        {
          "key": {
            "type": "Hash160",
            "value": "<funder1 contract hash>"
          },
          "value": {
            "type": "Array",
            "value": [
              {
                "type": "ECPoint",
                "value": "<funder1 pub key 1>"
              },
              {
                "type": "ECPoint",
                "value": "<funder1 pub key 2>"
              },
              ...
            ]
          }
        },
        {
          "key": {
            "type": "Hash160",
            "value": "<funder2 contract hash>"
          },
          "value": {
            "type": "Array",
            "value": [
              {
                "type": "ECPoint",
                "value": "<funder2 pub key 1>"
              }
            ]
          }
        },
        ...
      ]
    },
    {
      "type": "Map",
      "value": [
        {
          "key": {
            "type": "Hash160",
            "value": "<token1 contract hash>"
          },
          "value": {
            "type": "Integer",
            "value": "<token1 max funding amount>"
          }
        },
        {
          "key": {
            "type": "Hash160",
            "value": "<token2 contract hash>"
          },
          "value": {
            "type": "Integer",
            "value": "<token2 max funding amount>"
          }
        },
        ...
      ]
    },
    {
      "type": "Integer",
      "value": "<funders multi-sig threshold>"
    }
  ]
}
```

## Docs

Documentation about proposals, flow, etc., are in the official documentation page for GrantShares.

## Acknowledgement

[AxLabs](https://axlabs.com) develops and maintains the GrantShares contracts, with the support from the whole Neo
community.

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