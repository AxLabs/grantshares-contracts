// SPDX-License-Identifier: UNLICENSED
pragma solidity ^0.8.28;

import {AccessControlUpgradeable} from "@openzeppelin/contracts-upgradeable/access/AccessControlUpgradeable.sol";
import {Initializable} from "@openzeppelin/contracts-upgradeable/proxy/utils/Initializable.sol";
import {PausableUpgradeable} from "@openzeppelin/contracts-upgradeable/utils/PausableUpgradeable.sol";
import {UUPSUpgradeable} from "@openzeppelin/contracts-upgradeable/proxy/utils/UUPSUpgradeable.sol";

contract GrantSharesRelayer is AccessControlUpgradeable, PausableUpgradeable, UUPSUpgradeable {
    //0xfc512fde
    error InvalidPaymentAmount();

    event CreateProposal(address indexed sender, Proposal proposal);

    event ExecuteProposal(uint256 indexed proposalId);

    /// @custom:storage-location erc7201:grantshares.storage
    struct GSStorage {
        uint256 proposalFee;
        uint256 executionFee;
    }

    struct Proposal {
        bytes intent;
        string offchainUri;
        uint256 linkedProposal;
    }

    //keccak256(abi.encode(uint256(keccak256("grantshares.storage")) - 1)) & ~bytes32(uint256(0xff))
    bytes32 private constant GSStorageLocation = 0xbf1dcdeffa40dfe9cfe080762b5d715a0ec98727df31130b368e7f82f0d49800;

    /**
     * @dev Initializes the contract with the sender as the default admin role
     * @param proposalFee Fee required to create a proposal
     * @param executionFee Fee required to execute a proposal
     */
    function initialize(uint256 proposalFee, uint256 executionFee) public initializer {
        _grantRole(DEFAULT_ADMIN_ROLE, msg.sender);
        _getGSStorage().proposalFee = proposalFee;
        _getGSStorage().executionFee = executionFee;
    }

    /**
     * @dev Creates a proposal event with the proposal data
     *      This function requires an ETH payment as a fee, in order to create a proposal
     * @param proposal Proposal to be created
     */
    function propose(Proposal calldata proposal) external payable whenNotPaused {
        if (_getGSStorage().proposalFee != msg.value) revert InvalidPaymentAmount();
        emit CreateProposal(msg.sender, proposal);
    }

    /**
     * @dev Executes a proposal
     *      This function requires an ETH payment as a fee, in order to execute a proposal
     * @param proposalId Id of the proposal to be executed
     */
    function execute(uint256 proposalId) external payable whenNotPaused {
        if (_getGSStorage().executionFee != msg.value) revert InvalidPaymentAmount();
        emit ExecuteProposal(proposalId);
    }

    /**
     * @dev Sets the fee required to create a proposal
     * @param fee Fee to be paid to create a proposal
     */
    function setProposalFee(uint256 fee) external onlyRole(DEFAULT_ADMIN_ROLE) {
        _getGSStorage().proposalFee = fee;
    }

    /**
     * @dev Sets the fee required to execute a proposal
     * @param fee Fee to be paid to execute a proposal
     */
    function setExecutionFee(uint256 fee) external onlyRole(DEFAULT_ADMIN_ROLE) {
        _getGSStorage().executionFee = fee;
    }

    /**
     * @dev Returns the fee required to create a proposal
     * @return Fee required to create a proposal
     */
    function getFees() external pure returns (GSStorage memory) {
        return _getGSStorage();
    }

    /**
     * @dev Pauses the contract
     */
    function pause() external onlyRole(DEFAULT_ADMIN_ROLE) {
        _pause();
    }

    /**
     * @dev Unpauses the contract
     */
    function unpause() external onlyRole(DEFAULT_ADMIN_ROLE) {
        _unpause();
    }

    function _authorizeUpgrade(address) internal override onlyRole(DEFAULT_ADMIN_ROLE) {}

    function _getGSStorage() private pure returns (GSStorage storage $) {
        assembly {
            $.slot := GSStorageLocation
        }
    }
}
