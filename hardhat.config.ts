import fs from 'fs';
import '@typechain/hardhat';
import 'hardhat-preprocessor';
import '@openzeppelin/hardhat-upgrades';
import { HardhatUserConfig, task } from 'hardhat/config';
import checkBalance from './src/deploy/typescript/checkBalance';

function getRemappings() {
  return fs
    .readFileSync('remappings.txt', 'utf8')
    .split('\n')
    .filter(Boolean)
    .map((line) => line.trim().split('='));
}

task('checkBalance', 'Check deployer balance').setAction(checkBalance);

//!!!TEST MNEMONIC!!! - DO NOT USE IN PRODUCTION
const accountMnemonic =
  process.env.MNEMONIC || 'cabin remain mom audit drive system nurse sniff mule odor approve bread';

const config: HardhatUserConfig = {
  solidity: {
    version: '0.8.28',
    settings: {
      optimizer: {
        enabled: true,
        runs: 200,
      },
    },
  },
  networks: {
    hardhat: {
      forking: {
        url: 'https://sepolia.optimism.io',
      },
      accounts: {
        mnemonic: accountMnemonic,
      },
      chainId: 11155111,
      gas: 'auto',
      gasMultiplier: 1,
    },
    op_sepolia: {
      url: 'https://sepolia.optimism.io',
      accounts: {
        mnemonic: accountMnemonic,
      },
      chainId: 11155420,
      gas: 'auto',
      gasMultiplier: 1,
    },
  },
  paths: {
    sources: './src/main/solidity', // Use ./src rather than ./contracts as Hardhat expects
    cache: './cache_hardhat', // Use a different cache for Hardhat than Foundry
  },
  // This fully resolves paths for imports in the ./lib directory for Hardhat
  //@ts-ignore
  preprocess: {
    //@ts-ignore
    eachLine: (hre) => ({
      transform: (line: string) => {
        if (line.match(/^\s*import /i)) {
          getRemappings().forEach(([find, replace]) => {
            if (line.match(find)) {
              line = line.replace(find, replace);
            }
          });
        }
        return line;
      },
    }),
  },
};

export default config;
