// SPDX-License-Identifier: UNLICENSED
pragma solidity ^0.8.28;

import {Upgrades, Options} from "openzeppelin-foundry-upgrades/Upgrades.sol";
import {GrantSharesRelayer, PausableUpgradeable} from "../../main/solidity/GrantSharesRelayer.sol";

import {Test} from "forge-std/Test.sol";

contract GrantSharesRelayerTest is Test {
    address relayerProxyAddress;
    GrantSharesRelayer relayer;
    address owner = 0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266;

    uint256 constant DEFAULT_CREATE_FEE = 1;
    uint256 constant DEFAULT_EXECUTION_FEE = 2;

    function setUp() public {
        Options memory opts;
        opts.unsafeAllow = "constructor";

        relayerProxyAddress = Upgrades.deployUUPSProxy(
            "GrantSharesRelayer.sol",
            abi.encodeCall(GrantSharesRelayer.initialize, (owner, DEFAULT_CREATE_FEE, DEFAULT_EXECUTION_FEE)),
            opts
        );
        relayer = GrantSharesRelayer(relayerProxyAddress);
    }

    function testInitialState() public view {
        assertEq(relayer.owner(), owner);
        assert(!relayer.paused());
        assertEq(relayer.getFees().proposalFee, DEFAULT_CREATE_FEE);
        assertEq(relayer.getFees().executionFee, DEFAULT_EXECUTION_FEE);
    }

    function testProposalEvent() public {
        vm.expectEmit(true, true, false, true, address(relayer));
        emit GrantSharesRelayer.CreateProposal(address(this), GrantSharesRelayer.Proposal("intent", "offchainUri", 1));
        relayer.propose{value: DEFAULT_CREATE_FEE}(GrantSharesRelayer.Proposal("intent", "offchainUri", 1));
    }

    function testExecutionEvent() public {
        vm.expectEmit(true, true, false, true, address(relayer));
        emit GrantSharesRelayer.ExecuteProposal(1);
        relayer.execute{value: DEFAULT_EXECUTION_FEE}(1);
    }

    function testProposalFee() public {
        vm.prank(owner);
        uint256 newFee = 4;
        relayer.setProposalFee(newFee);
        assertEq(relayer.getFees().proposalFee, newFee);
        assertNotEq(DEFAULT_CREATE_FEE, newFee);

        // Fail without value
        vm.expectRevert(GrantSharesRelayer.InvalidPaymentAmount.selector);
        relayer.propose(GrantSharesRelayer.Proposal("intent", "offchainUri", 1));

        // Fail with wrong value
        vm.expectRevert(GrantSharesRelayer.InvalidPaymentAmount.selector);
        relayer.propose{value: DEFAULT_CREATE_FEE}(GrantSharesRelayer.Proposal("intent", "offchainUri", 1));

        vm.expectEmit(true, true, false, true, address(relayer));
        emit GrantSharesRelayer.CreateProposal(address(this), GrantSharesRelayer.Proposal("intent", "offchainUri", 1));
        relayer.propose{value: newFee}(GrantSharesRelayer.Proposal("intent", "offchainUri", 1));
    }

    function testExecutionFee() public {
        uint256 newFee = 5;
        vm.prank(owner);
        relayer.setExecutionFee(newFee);
        assertEq(relayer.getFees().executionFee, newFee);
        assertNotEq(DEFAULT_EXECUTION_FEE, newFee);

        // Fail without value
        vm.expectRevert(GrantSharesRelayer.InvalidPaymentAmount.selector);
        relayer.execute(1);

        // Fail with wrong value
        vm.expectRevert(GrantSharesRelayer.InvalidPaymentAmount.selector);
        relayer.execute{value: DEFAULT_EXECUTION_FEE}(1);

        vm.expectEmit(true, true, false, true, address(relayer));
        emit GrantSharesRelayer.ExecuteProposal(1);
        relayer.execute{value: newFee}(1);
    }

    function testWithdrawal() public {
        // Pay proposal fee
        relayer.propose{value: DEFAULT_CREATE_FEE}(GrantSharesRelayer.Proposal("intent", "offchainUri", 1));

        // Pay execution fee
        relayer.execute{value: DEFAULT_EXECUTION_FEE}(1);

        // Check initial balance of owner
        uint256 initialBalance = owner.balance;

        // Withdraw fees
        vm.prank(owner);
        relayer.withdrawFees();

        // Check final balance of owner
        uint256 finalBalance = owner.balance;

        // Assert that the final balance is increased by the sum of the fees
        assertEq(finalBalance, initialBalance + DEFAULT_CREATE_FEE + DEFAULT_EXECUTION_FEE);
    }

    function testPause() public {
        vm.prank(owner);
        relayer.pause();
        assert(relayer.paused());

        vm.expectRevert(PausableUpgradeable.EnforcedPause.selector);
        relayer.propose(GrantSharesRelayer.Proposal("intent", "offchainUri", 1));
        vm.expectRevert(PausableUpgradeable.EnforcedPause.selector);
        relayer.execute(1);

        vm.prank(owner);
        relayer.unpause();
        assert(!relayer.paused());

        vm.expectEmit(true, true, false, true, address(relayer));
        emit GrantSharesRelayer.CreateProposal(address(this), GrantSharesRelayer.Proposal("intent", "offchainUri", 1));
        relayer.propose{value: DEFAULT_CREATE_FEE}(GrantSharesRelayer.Proposal("intent", "offchainUri", 1));
        vm.expectEmit(true, true, false, true, address(relayer));
        emit GrantSharesRelayer.ExecuteProposal(1);
        relayer.execute{value: DEFAULT_EXECUTION_FEE}(1);
    }
}
