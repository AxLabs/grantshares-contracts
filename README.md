[![grantshares Actions Status](https://github.com/AxLabs/grantshares-contracts/workflows/grantshares-ci-cd/badge.svg)](https://github.com/AxLabs/grantshares-contracts/actions)

# GrantShares: Smart Contracts

This git repo contains the smart contracts for the GrantShares program.

If you want to know more about GrantShares go to [grantshares.io](https://grantshares.io) or read the documentation 
at [docs.grantshares.io](https://docs.grantshares.io).

The Governance and Treasury are deployed on Neo N3 mainnet.  
- GrantSharesGov: [`0xf15976ea5c020aaa12b9989aa9880e990eb5dcc9`](https://explorer.onegate.space/contractinfo/0xf15976ea5c020aaa12b9989aa9880e990eb5dcc9). 
- GrantSharesTreasury: [`0x6276c1e3a68280bc6c9c00df755fb691be1162ef`](https://explorer.onegate.space/contractinfo/0x6276c1e3a68280bc6c9c00df755fb691be1162ef)
- GrantSharesBridgeAdapter: [`0x8346705e69ecc085f2216b4836659686dbab59ee`](https://explorer.onegate.space/contractinfo/0x8346705e69ecc085f2216b4836659686dbab59ee)

The Relayer contract is deployed on Neo X mainnet.
- GrantSharesRelayer: [`0xEBE5BEcFF0D8BEf442ced1eBb042Eb7E9652b98B`](https://neoxscan.ngd.network/address/0xebe5becff0d8bef442ced1ebb042eb7e9652b98b)

## Development

Clone this repo:
```shell
git clone https://github.com/AxLabs/grantshares-contracts.git
```

### Neo N3 contracts
The GrantShares contracts use the [neow3j](https://neow3j.io) devpack and compiler which in turn uses Gradle as the 
build tool. The contracts are located in the `main` source set.

### Solidity contracts
Since this project also has solidity code, we need to install the foundry toolchain by running the following commands:
```shell
curl -L https://foundry.paradigm.xyz | bash 
source ~/.bashrc #this might differ if using a different shell
foundryup
```

### Testing
After installing these, the tests can be executed with the following command (Note that you need a running Docker daemon for the tests to work):

```shell
./gradlew test
```

## Deployment

### Neo N3 contracts
The scripts and configurations to deploy the neoN3 contracts are in the `deploy` source set.  
Some basic scripts for invoking the contracts via the neow3j SDK are also located there.

### Solidity contracts
//TODO: Add deployment instructions for solidity contracts

## Security Audit

The smart contracts have been audited by [Red4Sec](https://red4sec.com/en). The audit report can be found [here](https://bit.ly/3wZ14uI).

## Acknowledgement

GrantShares is developed and maintained by [AxLabs](https://axlabs.com), with the support from the whole Neo community.
