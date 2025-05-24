import { ethers } from 'hardhat';

async function test_storage() {
  const smallArray = '0x1f0c016d0c14f563ea40bc283d4d0e05c48ea305b3f2a07340ef41627d5b52';
  const midArray =
    '0x0c030102030c14f563ea40bc283d4d0e05c48ea305b3f2a07340ef01d00701e80314c01f0c04737761700c14f563ea40bc283d4d0e05c48ea305b3f2a07340ef41627d5b52';
  const largeArray =
    '0x1a0c3468747470733a2f2f6769746875622e636f6d2f41784c6162732f6772616e747368617265732d746573742f6973737565732f39351f00640c148bb952967eeb42b0ebd1ce42f4bed8f0cfbee5eb0c14f563ea40bc283d4d0e05c48ea305b3f2a07340ef13c00c066272696467650c14ee59abdb86966536486b21f285c0ec695e70468314c01f00640c14ee59abdb86966536486b21f285c0ec695e7046830c14f563ea40bc283d4d0e05c48ea305b3f2a07340ef13c00c0d72656c65617365546f6b656e730c14ab0f127df9e5f04f488a318f9b5786394f11dcc814c01f1a0c14ee59abdb86966536486b21f285c0ec695e7046830c14cf76e28bd0062c4a478ee35561011319f3cfa4d213c00c0d72656c65617365546f6b656e730c14ab0f127df9e5f04f488a318f9b5786394f11dcc814c013c00c140d165c9899c38bbf5991c5e47b04937258caec6914c01f0c0e63726561746550726f706f73616c0c14f563ea40bc283d4d0e05c48ea305b3f2a07340ef41627d5b52';

  console.log('Deploying Storage contract...');
  const Storage = await ethers.getContractFactory('Storage');
  const storage = await Storage.deploy();

  // Test storing different sized arrays
  console.log('\nTesting storage costs:');

  // Store small array
  const tx1 = await storage.store(smallArray);
  const receipt1 = await tx1.wait();

  // Store medium array
  const tx2 = await storage.store(midArray);
  const receipt2 = await tx2.wait();

  // Store large array
  const tx3 = await storage.store(largeArray);
  const receipt3 = await tx3.wait();

  // Test overwriting existing values

  // First store a small initial value at each test index
  console.log('Storing initial values...');
  const initialValue = '0x0102';
  await storage.storeAtIndex(10, initialValue);
  await storage.storeAtIndex(11, initialValue);
  await storage.storeAtIndex(12, initialValue);

  // Overwrite with small array
  const tx4 = await storage.storeAtIndex(10, smallArray);
  const receipt4 = await tx4.wait();

  // Overwrite with medium array
  const tx5 = await storage.storeAtIndex(11, midArray);
  const receipt5 = await tx5.wait();

  // Overwrite with large array
  const tx6 = await storage.storeAtIndex(12, largeArray);
  const receipt6 = await tx6.wait();

  // Print the comparison results in GAS used per tx
  // Values need to be multiplied by gasPrice to get the cost in ETH
  console.log('\nSize comparison:');
  console.log(
    'Small array size:',
    (smallArray.length - 2) / 2,
    'bytes',
    ' - storage gas used:',
    receipt1.gasUsed.toString(),
    ' - overwrite gas used:',
    receipt4.gasUsed.toString(),
  );
  console.log(
    'Medium array size:',
    (midArray.length - 2) / 2,
    'bytes',
    ' - storage gas used:',
    receipt2.gasUsed.toString(),
    ' - overwrite gas used:',
    receipt5.gasUsed.toString(),
  );
  console.log(
    'Large array size:',
    (largeArray.length - 2) / 2,
    'bytes',
    ' - storage gas used:',
    receipt3.gasUsed.toString(),
    ' - overwrite gas used:',
    receipt6.gasUsed.toString(),
  );
}

test_storage()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error(error);
    process.exit(1);
  });
