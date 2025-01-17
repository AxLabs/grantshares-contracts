const { ethers, upgrades } = require('hardhat');

async function main() {
  const GrantSharesRelayer = await ethers.getContractFactory('GrantSharesRelayer');
  console.log('Deploying GrantSharesRelayer...');
  //TODO: get these from an env file
  const relayerProxy = await upgrades.deployProxy(GrantSharesRelayer, [0, 0], {
    initializer: 'initialize',
    kind: 'uups',
  });
  await relayerProxy.waitForDeployment();
  //TODO: save the address to a file alongside the ABI
  console.log('GrantSharesRelayer deployed to:', await relayerProxy.getAddress());
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error(error);
    process.exit(1);
  });
