import { HardhatRuntimeEnvironment } from 'hardhat/types/runtime';
import "@nomicfoundation/hardhat-ethers";

export default async function checkBalance(params: any, hre: HardhatRuntimeEnvironment): Promise<void> {
  const ethers = hre.ethers;

  const [account] = await ethers.getSigners();

  console.log(
    `Balance for 1st account ${await account.getAddress()}: ${await ethers.provider.getBalance(account.getAddress())}`,
  );
}
