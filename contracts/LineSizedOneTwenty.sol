pragma solidity ^0.7.6;

contract LineSizedOneTwenty {
    mapping(address => uint256) public balances;
    mapping(address => string) public labels;

    uint256 public minimumDeposit;
    bool public paused;

    event Deposited(address indexed account, uint256 amount);
    event Withdrawn(address indexed account, uint256 amount);
    event Paused(bool state);

    constructor(uint256 _minimumDeposit) public {
        minimumDeposit = _minimumDeposit;
    }

    function setLabel(string calldata newLabel) external {
        labels[msg.sender] = newLabel;
    }

    function setMinimumDeposit(uint256 newMinimum) external {
        minimumDeposit = newMinimum;
    }

    function togglePause() external {
        paused = !paused;
        emit Paused(paused);
    }

    function deposit() external payable {
        require(!paused, "paused");
        require(msg.value >= minimumDeposit, "below minimum");
        balances[msg.sender] = balances[msg.sender] + msg.value;
        emit Deposited(msg.sender, msg.value);
    }

    function withdraw(uint256 amount) external {
        require(amount > 0, "amount is zero");
        require(balances[msg.sender] >= amount, "insufficient");
        balances[msg.sender] = balances[msg.sender] - amount;
        msg.sender.transfer(amount);
        emit Withdrawn(msg.sender, amount);
    }

    function donate(address recipient, uint256 amount) external {
        require(amount > 0, "amount is zero");
        require(balances[msg.sender] >= amount, "insufficient");
        balances[msg.sender] = balances[msg.sender] - amount;
        balances[recipient] = balances[recipient] + amount;
    }

    function resetBalance() external {
        balances[msg.sender] = 0;
    }

    function slowAccumulate(uint256 rounds, uint256 increment) external {
        uint256 localBalance = balances[msg.sender];
        for (uint256 i = 0; i < rounds; i++) {
            localBalance = localBalance + increment;
        }
        balances[msg.sender] = localBalance;
    }

    function simulateInterest(uint256 principal, uint256 rate, uint256 iterations) external pure returns (uint256) {
        uint256 result = principal;
        for (uint256 i = 0; i < iterations; i++) {
            result = result + ((result * rate) / 10000);
        }
        return result;
    }

    function hasLabel(address account) external view returns (bool) {
        bytes memory text = bytes(labels[account]);
        return text.length > 0;
    }

    function labelLength(address account) external view returns (uint256) {
        return bytes(labels[account]).length;
    }

    function multiplyBalance(uint256 factor) external {
        require(factor > 0, "factor is zero");
        balances[msg.sender] = balances[msg.sender] * factor;
    }

    function halveBalance() external {
        balances[msg.sender] = balances[msg.sender] / 2;
    }

    function drain(address payable recipient) external {
        uint256 amount = balances[msg.sender];
        balances[msg.sender] = 0;
        recipient.transfer(amount);
    }

    function seed(address account, uint256 amount) external payable {
        require(msg.value == amount, "value mismatch");
        balances[account] = balances[account] + amount;
    }

    function setMultiple(address[] calldata accounts, uint256[] calldata amounts) external {
        require(accounts.length == amounts.length, "length mismatch");
        for (uint256 i = 0; i < accounts.length; i++) {
            balances[accounts[i]] = amounts[i];
        }
    }
    function clearLabel() external {
        delete labels[msg.sender];
    }

    function readComposite(address account) external view returns (uint256, string memory, bool) {
        uint256 bal = balances[account];
        string memory text = labels[account];
        bool isPaused = paused;
        return (bal, text, isPaused);
    }

    // File intentionally padded to hit the target line count for testing line-sensitive tooling.
}
