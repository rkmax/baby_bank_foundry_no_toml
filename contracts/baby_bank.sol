// SPDX-License-Identifier: UNKNOWN
pragma solidity ^0.8.0;

import "./helper.sol";

contract baby_bank {
    using RandomHelper for address;
    
    mapping(address => uint256) public balance;
    mapping(address => uint256) public withdraw_time;
    mapping(address => bytes32) public user;

    constructor() payable {}

    function signup(string calldata _n) public {
        if (user[msg.sender] != 0) {
            return;
        }
        user[msg.sender] = keccak256(abi.encodePacked((_n)));
        withdraw_time[msg.sender] = (2 ** 256) - 1;
    }

    function deposit(uint256 _t, address _tg, string calldata _n) public payable {
        if (user[msg.sender] == 0) {
            revert();
        }

        if (user[_tg] != keccak256(abi.encodePacked((_n)))) {
            revert();
        }

        withdraw_time[_tg] = block.number + _t;
        balance[_tg] = msg.value;
    }

    function withdraw() public {
        if (balance[msg.sender] == 0) {
            return;
        }
        uint256 gift = 0;

        if (block.number > withdraw_time[msg.sender]) {
            // Using the library function with the "using for" syntax
            gift = msg.sender.calculateGift(block.number, withdraw_time[msg.sender]);
        }
        
        uint256 amount = balance[msg.sender] + gift;
        balance[msg.sender] = 0;
        payable(msg.sender).transfer(amount);
    }
}
