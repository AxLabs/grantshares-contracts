import { vars } from 'hardhat/config';

/**
 * Tests the creation and execution of a proposal using the GrantSharesRelayer contract
 * 
 * You need to provide the right address and parameters for your use case, depending on the network you are using
 */
async function test_events() {
  const GrantSharesRelayer = await ethers.getContractFactory('GrantSharesRelayer');

  const grantSharesRelayerAddress = '0x90D1c342E394aaFD6dD49aaBA976eEEfF65ACa86';
  const relayerProxy = await GrantSharesRelayer.attach(grantSharesRelayerAddress);

  const proposal = {
    offchainUri: 'https://github.com/AxLabs/grantshares-dev/issues/471',
    // linkedProposal: 247,
    linkedProposal:
      '115792089237316195423570985008687907853269984665640564039457584007913129639935', //maxint
    intents:
      '0x1f110c140768ccdb99f2dad55dc46f1c256a97e71ba1a8b00c14f563ea40bc283d4d0e05c48ea305b3f2a07340ef13c00c066272696467650c14ee59abdb86966536486b21f285c0ec695e70468314c01f110c14ee59abdb86966536486b21f285c0ec695e7046830c14f563ea40bc283d4d0e05c48ea305b3f2a07340ef13c00c0d72656c65617365546f6b656e730c14ab0f127df9e5f04f488a318f9b5786394f11dcc814c01f02809698000c14ee59abdb86966536486b21f285c0ec695e7046830c14cf76e28bd0062c4a478ee35561011319f3cfa4d213c00c0d72656c65617365546f6b656e730c14ab0f127df9e5f04f488a318f9b5786394f11dcc814c013c0',
  };
  const proposalId = 268;


  const fees = await relayerProxy.getFees();
  const proposalFee = fees.proposalFee;
  const executionFee = fees.executionFee;

  // region - set fees
  console.log('Fees:', proposalFee.toString(), executionFee.toString());
  // await relayerProxy.setProposalFee(ethers.parseEther('0.002'));
  // await relayerProxy.setExecutionFee(ethers.parseEther('0.1'));
  // let newFees = await relayerProxy.getFees();
  // console.log('New fees:', newFees);
  // endregion

  // region - interact with contract
  // await relayerProxy.propose(proposal, { value: proposalFee });
  // await relayerProxy.execute(proposalId, { value: executionFee });
  // await relayerProxy.withdrawFees().catch((error: any) => {
  //   console.log(error);
  // });

  let owner = await relayerProxy.owner();
  console.log('Owner:', owner);
  // endregion
}

test_events()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error(error);
    process.exit(1);
  });
