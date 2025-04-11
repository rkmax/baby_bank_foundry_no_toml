// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

// Vulnerable baby example of a signature-based smart contract
contract BabyVulnerable {
    address public owner;

    constructor() {
        owner = msg.sender;
    }

    // Sensitive function that MUST verify signature properly but fails to do so
    function dangerousAction(bytes32 messageHash, bytes memory signature) public {
        // Incorrectly missing signature validation entirely.
        // Anyone can call this function and pretend to act as the owner by providing any random signature.

        performSensitiveAction(messageHash); // vulnerable! no validation!
    }

    function performSensitiveAction(bytes32 messageHash) internal {
        // Perform important/sensitive action
        // In real usage, here we would carry out sensitive logic
    }

    receive() external payable {
        // solhint-disable-next-line reason-string, gas-custom-errors
        revert();
    }
}