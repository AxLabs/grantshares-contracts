// SPDX-License-Identifier: UNLICENSED
pragma solidity ^0.8.28;

import "../../main/solidity/GrantSharesRelayer.sol";

import "forge-std/Test.sol";
import "forge-std/Vm.sol";

contract GrantSharesRelayerTest is Test {
    GrantSharesRelayer relayer;
    Vm.Wallet wallet;

    function setUp() public {
        relayer = new GrantSharesRelayer();
        relayer.initialize(0, 0);
    }

    function testInitialState() public view {
        bytes32 DEFAULT_ADMIN_ROLE = 0x00;
        assert(relayer.hasRole(DEFAULT_ADMIN_ROLE, address(this)));
        assert(!relayer.paused());
        assertEq(relayer.getFees().proposalFee, 0);
        assertEq(relayer.getFees().executionFee, 0);
    }

    function testProposalEvent() public {
        vm.expectEmit(true, true, false, true, address(relayer));
        emit GrantSharesRelayer.CreateProposal(address(this), GrantSharesRelayer.Proposal("intent", "offchainUri", 1));
        relayer.propose(GrantSharesRelayer.Proposal("intent", "offchainUri", 1));
    }

    function testExecutionEvent() public {
        vm.expectEmit(true, true, false, true, address(relayer));
        emit GrantSharesRelayer.ExecuteProposal(1);
        relayer.execute(1);
    }

    function testProposalFee() public {
        relayer.setProposalFee(1);
        assertEq(relayer.getFees().proposalFee, 1);
        vm.expectRevert(GrantSharesRelayer.InvalidPaymentAmount.selector);
        relayer.propose(GrantSharesRelayer.Proposal("intent", "offchainUri", 1));

        vm.expectRevert(GrantSharesRelayer.InvalidPaymentAmount.selector);
        relayer.propose{value: 2}(GrantSharesRelayer.Proposal("intent", "offchainUri", 1));

        vm.expectEmit(true, true, false, true, address(relayer));
        emit GrantSharesRelayer.CreateProposal(address(this), GrantSharesRelayer.Proposal("intent", "offchainUri", 1));
        relayer.propose{value: 1}(GrantSharesRelayer.Proposal("intent", "offchainUri", 1));
    }

    function testExecutionFee() public {
        relayer.setExecutionFee(1);
        assertEq(relayer.getFees().executionFee, 1);
        vm.expectRevert(GrantSharesRelayer.InvalidPaymentAmount.selector);
        relayer.execute(1);

        vm.expectRevert(GrantSharesRelayer.InvalidPaymentAmount.selector);
        relayer.execute{value: 2}(1);

        vm.expectEmit(true, true, false, true, address(relayer));
        emit GrantSharesRelayer.ExecuteProposal(1);
        relayer.execute{value: 1}(1);
    }

    function testPause() public {
        relayer.pause();
        assert(relayer.paused());
        vm.expectRevert(PausableUpgradeable.EnforcedPause.selector);
        relayer.propose(GrantSharesRelayer.Proposal("intent", "offchainUri", 1));
        vm.expectRevert(PausableUpgradeable.EnforcedPause.selector);
        relayer.execute(1);

        relayer.unpause();
        assert(!relayer.paused());
        vm.expectEmit(true, true, false, true, address(relayer));
        emit GrantSharesRelayer.CreateProposal(address(this), GrantSharesRelayer.Proposal("intent", "offchainUri", 1));
        relayer.propose(GrantSharesRelayer.Proposal("intent", "offchainUri", 1));
        vm.expectEmit(true, true, false, true, address(relayer));
        emit GrantSharesRelayer.ExecuteProposal(1);
        relayer.execute(1);
    }
}
