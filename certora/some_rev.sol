// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

/**
 * @title VulnerableToken
 * @dev A token contract with several vulnerabilities for demonstration purposes
 * useless comment to confirm 
 */
contract VulnerableToken {
    mapping(address => uint256) public balances;
    mapping(address => mapping(address => uint256)) public allowances;
    address public owner;
    uint256 public totalSupply;
    string public name = "VulnerableToken";
    string public symbol = "VULN";
    uint8 public decimals = 18;

    event Transfer(address indexed from, address indexed to, uint256 value);
    event Approval(address indexed owner, address indexed spender, uint256 value);

    constructor() {
        owner = msg.sender;
        totalSupply = 1000000 * (10 ** decimals);
        balances[msg.sender] = totalSupply;
    }

    // Vulnerability 1: No input validation (allowing overflow in older Solidity)
    function transfer(address to, uint256 amount) public returns (bool) {
        balances[msg.sender] -= amount;  // Potential underflow if amount > balances[msg.sender]
        balances[to] += amount;
        emit Transfer(msg.sender, to, amount);
        return true;
    }

    // Vulnerability 2: Missing zero address check
    function approve(address spender, uint256 amount) public returns (bool) {
        allowances[msg.sender][spender] = amount;
        emit Approval(msg.sender, spender, amount);
        return true;
    }

    // Vulnerability 3: No checks-effects-interactions pattern
    function withdrawAll() public {
        uint256 amount = balances[msg.sender];
        // Sends ETH before updating balance (reentrancy vulnerability)
        (bool success, ) = msg.sender.call{value: amount}("");
        require(success, "Transfer failed");
        balances[msg.sender] = 0;
    }

    // Vulnerability 4: Weak access control
    function mint(address to, uint256 amount) public {
        // Anyone can mint tokens - no ownership check
        balances[to] += amount;
        totalSupply += amount;
        emit Transfer(address(0), to, amount);
    }

    // Vulnerability 5: Incorrect use of tx.origin
    function transferFrom(address from, address to, uint256 amount) public returns (bool) {
        // Using tx.origin instead of msg.sender for authorization
        require(tx.origin == from, "Not authorized");
        require(allowances[from][msg.sender] >= amount, "Insufficient allowance");
        
        allowances[from][msg.sender] -= amount;
        balances[from] -= amount;
        balances[to] += amount;
        
        emit Transfer(from, to, amount);
        return true;
    }

    // Vulnerability 6: Unchecked external call
    function distributeTokens(address[] memory recipients, uint256[] memory amounts) public {
        for (uint i = 0; i < recipients.length; i++) {
            // Could be used to DoS with unbounded gas consumption
            transfer(recipients[i], amounts[i]);
        }
    }
}
