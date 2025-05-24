// SPDX-License-Identifier: UNLICENSED
pragma solidity ^0.8.28;

contract Storage {
    // Storage variables
    mapping(uint256 => bytes) private dataMap;
    uint256 private count;

    // Events
    event DataStored(uint256 indexed index, bytes data);

    /**
     * @dev Stores data in the next available index
     * @param data The byte array to store
     * @return The index where the data was stored
     */
    function store(bytes calldata data) external returns (uint256) {
        uint256 currentIndex = count;
        dataMap[currentIndex] = data;
        count++;

        emit DataStored(currentIndex, data);
        return currentIndex;
    }

    /**
     * @dev Stores data at a specific index
     * @param index The index where to store the data
     * @param data The byte array to store
     */
    function storeAtIndex(uint256 index, bytes calldata data) external {
        dataMap[index] = data;
        emit DataStored(index, data);
    }

    /**
     * @dev Retrieves data from a specific index
     * @param index The index to retrieve data from
     * @return The stored byte array
     */
    function retrieve(uint256 index) external view returns (bytes memory) {
        return dataMap[index];
    }

    /**
     * @dev Gets the total number of stored items
     * @return The count of stored items
     */
    function getCount() external view returns (uint256) {
        return count;
    }
}

