pragma solidity ^0.7.6;

contract LineSizedTwoHundred {
    struct LedgerEntry {
        uint256 amount;
        uint256 blockNumber;
        bool credited;
    }

    struct AccountInfo {
        uint256 total;
        uint256 tag;
        bool frozen;
        LedgerEntry[] history;
    }

    mapping(address => AccountInfo) private accounts;
    uint256 public globalLimit;
    bool public halted;

    event Deposited(address indexed account, uint256 amount, uint256 tag);
    event Withdrawn(address indexed account, uint256 amount);
    event LimitUpdated(uint256 newLimit);
    event StatusChanged(bool haltedState);

    constructor(uint256 _globalLimit) public {
        globalLimit = _globalLimit;
    }

    function setTag(uint256 tag) external {
        AccountInfo storage account = accounts[msg.sender];
        account.tag = tag;
    }

    function toggleHalt() external {
        halted = !halted;
        emit StatusChanged(halted);
    }

    function setGlobalLimit(uint256 newLimit) external {
        globalLimit = newLimit;
        emit LimitUpdated(newLimit);
    }

    function freeze(address target) external {
        accounts[target].frozen = true;
    }

    function unfreeze(address target) external {
        accounts[target].frozen = false;
    }

    function deposit() external payable {
        require(!halted, "halted");
        AccountInfo storage account = accounts[msg.sender];
        require(!account.frozen, "frozen");
        require(msg.value > 0, "zero value");
        account.total = account.total + msg.value;
        account.history.push(LedgerEntry({amount: msg.value, blockNumber: block.number, credited: true}));
        emit Deposited(msg.sender, msg.value, account.tag);
    }

    function withdraw(uint256 amount) external {
        require(!halted, "halted");
        AccountInfo storage account = accounts[msg.sender];
        require(!account.frozen, "frozen");
        require(account.total >= amount, "insufficient");
        account.total = account.total - amount;
        account.history.push(LedgerEntry({amount: amount, blockNumber: block.number, credited: false}));
        msg.sender.transfer(amount);
        emit Withdrawn(msg.sender, amount);
    }

    function sweep(address payable recipient, uint256 amount) external {
        require(!halted, "halted");
        AccountInfo storage account = accounts[msg.sender];
        require(account.total >= amount, "insufficient");
        account.total = account.total - amount;
        recipient.transfer(amount);
        account.history.push(LedgerEntry({amount: amount, blockNumber: block.number, credited: false}));
        emit Withdrawn(msg.sender, amount);
    }

    function loadHistory(address account) external view returns (uint256[] memory amounts, uint256[] memory blocks, bool[] memory credited) {
        LedgerEntry[] storage entries = accounts[account].history;
        uint256 length = entries.length;
        amounts = new uint256[](length);
        blocks = new uint256[](length);
        credited = new bool[](length);
        for (uint256 i = 0; i < length; i++) {
            amounts[i] = entries[i].amount;
            blocks[i] = entries[i].blockNumber;
            credited[i] = entries[i].credited;
        }
        return (amounts, blocks, credited);
    }

    function latestEntry(address account) external view returns (uint256, uint256, bool) {
        LedgerEntry[] storage entries = accounts[account].history;
        require(entries.length > 0, "no history");
        LedgerEntry storage item = entries[entries.length - 1];
        return (item.amount, item.blockNumber, item.credited);
    }

    function cappedTotal(address account) external view returns (uint256) {
        uint256 balance = accounts[account].total;
        if (balance > globalLimit) {
            return globalLimit;
        }
        return balance;
    }

    function accumulate(address account) external view returns (uint256 totalCredits, uint256 totalDebits) {
        LedgerEntry[] storage entries = accounts[account].history;
        for (uint256 i = 0; i < entries.length; i++) {
            if (entries[i].credited) {
                totalCredits = totalCredits + entries[i].amount;
            } else {
                totalDebits = totalDebits + entries[i].amount;
            }
        }
        return (totalCredits, totalDebits);
    }

    function clearHistory() external {
        delete accounts[msg.sender].history;
    }

    function resetBalance() external {
        accounts[msg.sender].total = 0;
    }

    function copyTag(address from, address to) external {
        accounts[to].tag = accounts[from].tag;
    }

    function requireMinimum(address account, uint256 minValue) external view returns (bool) {
        return accounts[account].total >= minValue;
    }

    // Padding section below keeps the file at exactly 200 lines for testing.
    function filler1() external pure returns (uint256) { return 1; }
    function filler2() external pure returns (uint256) { return 2; }
    function filler3() external pure returns (uint256) { return 3; }
    function filler4() external pure returns (uint256) { return 4; }
    function filler5() external pure returns (uint256) { return 5; }
    function filler6() external pure returns (uint256) { return 6; }
    function filler7() external pure returns (uint256) { return 7; }
    function filler8() external pure returns (uint256) { return 8; }
    function filler9() external pure returns (uint256) { return 9; }
    function filler10() external pure returns (uint256) { return 10; }
    function filler11() external pure returns (uint256) { return 11; }
    function filler12() external pure returns (uint256) { return 12; }
    function filler13() external pure returns (uint256) { return 13; }
    function filler14() external pure returns (uint256) { return 14; }
    function filler15() external pure returns (uint256) { return 15; }
    function filler16() external pure returns (uint256) { return 16; }
    function filler17() external pure returns (uint256) { return 17; }
    function filler18() external pure returns (uint256) { return 18; }
    function filler19() external pure returns (uint256) { return 19; }
    function filler20() external pure returns (uint256) { return 20; }
    function filler21() external pure returns (uint256) { return 21; }
    function filler22() external pure returns (uint256) { return 22; }
    function filler23() external pure returns (uint256) { return 23; }
    function filler24() external pure returns (uint256) { return 24; }
    function filler25() external pure returns (uint256) { return 25; }
    function filler26() external pure returns (uint256) { return 26; }
    function filler27() external pure returns (uint256) { return 27; }
    function filler28() external pure returns (uint256) { return 28; }
    function filler29() external pure returns (uint256) { return 29; }
    function filler30() external pure returns (uint256) { return 30; }
    function filler31() external pure returns (uint256) { return 31; }
    function filler32() external pure returns (uint256) { return 32; }
    function filler33() external pure returns (uint256) { return 33; }
    function filler34() external pure returns (uint256) { return 34; }
    function filler35() external pure returns (uint256) { return 35; }
    function filler36() external pure returns (uint256) { return 36; }
    function filler37() external pure returns (uint256) { return 37; }
    function filler38() external pure returns (uint256) { return 38; }
    function filler39() external pure returns (uint256) { return 39; }
    function filler40() external pure returns (uint256) { return 40; }
    function filler41() external pure returns (uint256) { return 41; }
    function filler42() external pure returns (uint256) { return 42; }
    function filler43() external pure returns (uint256) { return 43; }
    function filler44() external pure returns (uint256) { return 44; }
    function filler45() external pure returns (uint256) { return 45; }
    function filler46() external pure returns (uint256) { return 46; }
    function filler47() external pure returns (uint256) { return 47; }
    function filler48() external pure returns (uint256) { return 48; }
    function filler49() external pure returns (uint256) { return 49; }
    function filler50() external pure returns (uint256) { return 50; }
    function filler51() external pure returns (uint256) { return 51; }
    function filler52() external pure returns (uint256) { return 52; }
    function filler53() external pure returns (uint256) { return 53; }
    function filler54() external pure returns (uint256) { return 54; }
    function filler55() external pure returns (uint256) { return 55; }
    function filler56() external pure returns (uint256) { return 56; }
    function filler57() external pure returns (uint256) { return 57; }
    function filler58() external pure returns (uint256) { return 58; }
}
