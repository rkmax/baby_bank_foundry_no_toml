// SPDX-License-Identifier: UNKNOWN
pragma solidity ^0.8.0;

library RandomHelper {
    // Generate a random number between 0 and maxValue-1
    function generateRandom(uint256 blockNumber, address sender, uint256 maxValue) 
        internal 
        pure 
        returns (uint256) 
    {
        return uint256(keccak256(abi.encodePacked(blockNumber, sender))) % maxValue;
    }
    
    // Calculate a gift amount based on randomness and withdraw time
    function calculateGift(address sender, uint256 blockNumber, uint256 withdrawTime) 
        internal 
        pure 
        returns (uint256) 
    {
        uint256 gift = 0;
        uint256 lucky = generateRandom(blockNumber, sender, 10);
        
        if (lucky == 0) {
            gift = (10 ** 15) * withdrawTime;
        }
        
        return gift;
    }
}