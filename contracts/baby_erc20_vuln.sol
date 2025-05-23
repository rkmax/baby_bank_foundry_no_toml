// SPDX-License-Identifier: UNKNOWN
pragma solidity ^0.8.0;

/**
 * @title VulnerableBabyToken
 * @dev An ERC20 token implementation with deliberate security vulnerabilities for educational purposes
 * WARNING: DO NOT USE IN PRODUCTION
 */
contract VulnerableBabyToken {
    string public name = "VulnerableBabyToken";
    string public symbol = "VBABY";
    uint8 public decimals = 18;
    uint256 public totalSupply;
    
    // Owner of the contract
    address public owner;
    
    // Balances for each account
    mapping(address => uint256) public balanceOf;
    
    // Allowances for each account
    mapping(address => mapping(address => uint256)) public allowance;
    
    // Events required by the ERC20 standard
    event Transfer(address indexed from, address indexed to, uint256 value);
    event Approval(address indexed owner, address indexed spender, uint256 value);
    
    /**
     * @dev Constructor that gives the msg.sender all of existing tokens
     */
    constructor(uint256 initialSupply) {
        owner = msg.sender;
        totalSupply = initialSupply * 10 ** uint256(decimals);
        balanceOf[msg.sender] = totalSupply;
        emit Transfer(address(0), msg.sender, totalSupply);
    }
    
    /**
     * @dev Transfer tokens to a specified address
     */
    function transfer(address _to, uint256 _value) public returns (bool) {
        // Does not check if _to is address(0)
        // Does not check if _value is zero, allowing zero-value transfers
        // Does not check if msg.sender has sufficient balance before subtraction
        
        balanceOf[msg.sender] -= _value;
        balanceOf[_to] += _value;
        
        emit Transfer(msg.sender, _to, _value);
        return true;
    }
    
    /**
     * @dev Approve the passed address to spend the specified amount of tokens on behalf of msg.sender
     */
    function approve(address _spender, uint256 _value) public returns (bool) {
        // This approve function is vulnerable to the "allowance double-spend" exploit
        // The proper way is to first reduce allowance to 0, then set it to the new value
        allowance[msg.sender][_spender] = _value;
        emit Approval(msg.sender, _spender, _value);
        return true;
    }
    
    /**
     * @dev Transfer tokens from one address to another
     * VULNERABILITY: Missing return value check
     */
    function transferFrom(address _from, address _to, uint256 _value) public returns (bool) {
        require(balanceOf[_from] >= _value, "Insufficient balance");
        require(allowance[_from][msg.sender] >= _value, "Insufficient allowance");
        
        // VULNERABILITY: No check if _to is address(0)
        balanceOf[_from] -= _value;
        balanceOf[_to] += _value;
        allowance[_from][msg.sender] -= _value;
        
        emit Transfer(_from, _to, _value);
        return true;
    }
    
    /**
     * @dev Mint new tokens
     * VULNERABILITY: No access control
     */
    function mint(address _to, uint256 _amount) public returns (bool) {
        // VULNERABILITY: Missing access control - anyone can mint tokens
        // Should be: require(msg.sender == owner, "Only owner can mint");
        
        totalSupply += _amount;
        balanceOf[_to] += _amount;
        emit Transfer(address(0), _to, _amount);
        return true;
    }
    
    /**
     * @dev Burn tokens
     * VULNERABILITY: No check for sufficient balance
     */
    function burn(uint256 _amount) public returns (bool) {
        // VULNERABILITY: Missing balance check
        // Should be: require(balanceOf[msg.sender] >= _amount, "Insufficient balance");
        
        balanceOf[msg.sender] -= _amount; // Could underflow if _amount > balanceOf[msg.sender]
        totalSupply -= _amount;
        emit Transfer(msg.sender, address(0), _amount);
        return true;
    }
    
    /**
     * VULNERABILITY: Dangerous fallback function that gives tokens to anyone who sends ETH
     */
    receive() external payable {
        // VULNERABILITY: Giving tokens to anyone who sends ETH without any access control
        balanceOf[msg.sender] += msg.value * 1000;
        totalSupply += msg.value * 1000;
        emit Transfer(address(0), msg.sender, msg.value * 1000);
    }
    
    /**
     * VULNERABILITY: Reentrancy vulnerability
     */
    function withdrawDonations() public {
        // VULNERABILITY: Classic reentrancy vulnerability
        uint256 amount = balanceOf[msg.sender];
        
        // VULNERABILITY: State changes after external call
        (bool success, ) = msg.sender.call{value: amount}("");
        require(success, "Transfer failed");
        
        // This line happens after the external call, allowing reentrancy
        balanceOf[msg.sender] = 0;
    }
    
    /**
     * VULNERABILITY: Unchecked return value
     */
    function transferBatch(address[] memory _recipients, uint256[] memory _values) public returns (bool) {
        require(_recipients.length == _values.length, "Arrays must have same length");
        
        for (uint i = 0; i < _recipients.length; i++) {
            // VULNERABILITY: Not checking the return value of transfer
            // If one transfer fails, the function will continue processing other transfers
            transfer(_recipients[i], _values[i]);
        }
        
        return true;
    }
    
    /**
     * VULNERABILITY: Weak random number generation
     */
    function airdrop() public {
        // VULNERABILITY: Predictable random number
        uint256 randomAmount = uint256(keccak256(abi.encodePacked(block.timestamp, msg.sender))) % 1000;
        balanceOf[msg.sender] += randomAmount;
        totalSupply += randomAmount;
        emit Transfer(address(0), msg.sender, randomAmount);
    }
}
