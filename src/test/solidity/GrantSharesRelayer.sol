// SPDX-License-Identifier: UNLICENSED
pragma solidity ^0.8.13;

import "forge-std/Test.sol";

import "../../main/solidity/GrantSharesRelayer.sol";

contract GrantSharesRelayerTest is Test {
    GrantSharesRelayer relayer;

    function setUp() public {
        relayer = new GrantSharesRelayer();
    }

    function testEvent() public {
        string [] memory params = new string[](2);
        vm.expectEmit(true, true, false, true, address(relayer));
        emit GrantSharesRelayer.CreateProposal(address(this), "whatever", GrantSharesRelayer.Intent("target", "method", params));
        relayer.propose("whatever", GrantSharesRelayer.Intent("target", "method", params));
    }
}
