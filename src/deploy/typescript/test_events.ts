const { ethers: any } = require('hardhat');

async function test_events() {
  const GrantSharesRelayer = await ethers.getContractFactory('GrantSharesRelayer');
  // console.log('Deploying GrantSharesRelayer...');
  //TODO: get these from an env file

  const relayerProxy = await GrantSharesRelayer.attach(
    '0x9b82Ae1050c7C71cDA41E269b8F83478a01D71aE',
  );

  const proposal = {
    intents:
      '0x1f110c1434532057d32923c4d6ffcd033b46c6e7e2304b4b0c14f563ea40bc283d4d0e05c48ea305b3f2a07340ef13c00c066272696467650c14d936f0f8629a6979d8728c725f8b91b96c7031fc14c01f110c14d936f0f8629a6979d8728c725f8b91b96c7031fc0c14f563ea40bc283d4d0e05c48ea305b3f2a07340ef13c00c0d72656c65617365546f6b656e730c14ab0f127df9e5f04f488a318f9b5786394f11dcc814c01f02809698000c14d936f0f8629a6979d8728c725f8b91b96c7031fc0c14cf76e28bd0062c4a478ee35561011319f3cfa4d213c00c0d72656c65617365546f6b656e730c14ab0f127df9e5f04f488a318f9b5786394f11dcc814c013c0',
    offchainUri: 'https://github.com/AxLabs/grantshares-dev/issues/450',
    linkedProposal: '0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff',
    // linkedProposal: 247,
  };
  const proposalId = 247;

  await relayerProxy.propose(proposal);
  // await relayerProxy.execute(proposalId);
}

test_events()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error(error);
    process.exit(1);
  });
