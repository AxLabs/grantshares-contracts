// SPDX-License-Identifier: UNLICENSED
pragma solidity ^0.8.20;

import {AccessControl} from "oz/access/AccessControl.sol";

contract GrantSharesRelayer is AccessControl {
    struct Intent {
        string targetContract;
        string method;
        string[] params;
    }

    event CreateProposal(address indexed sender, bytes signature, Intent intent);

    constructor() {
        _grantRole(DEFAULT_ADMIN_ROLE, msg.sender);
    }

    function propose(bytes calldata signature, Intent calldata intent) public payable {
        //TODO: verify signature
        emit CreateProposal(msg.sender, signature, intent);
    }
}
