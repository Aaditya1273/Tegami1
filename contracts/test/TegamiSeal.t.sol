// SPDX-License-Identifier: MIT
pragma solidity ^0.8.26;

import {Test} from "forge-std/Test.sol";
import {TegamiSeal} from "../src/TegamiSeal.sol";
import {TestDollar} from "../src/TestDollar.sol";

contract TegamiSealTest is Test {
    TegamiSeal seal;
    TestDollar usd;
    address studio = address(0x5711);
    address freelancer = address(0xF00D);

    function setUp() public {
        seal = new TegamiSeal();
        usd = new TestDollar();
        usd.mint(studio, 5_000e6);
        vm.prank(studio);
        usd.approve(address(seal), type(uint256).max);
    }

    function test_payMovesMoneyAndRecordsLetterSignedByPayer() public {
        vm.prank(studio);
        uint256 i = seal.pay(address(usd), freelancer, 1_250e6, "INV-1042", "October retainer");
        assertEq(i, 0);
        assertEq(usd.balanceOf(freelancer), 1_250e6);
        assertEq(seal.letterCount(freelancer), 1);
        (address token, address payer, uint256 amount,,, string memory invoice, string memory note) = seal.letterAt(freelancer, 0);
        assertEq(token, address(usd));
        assertEq(payer, studio);
        assertEq(amount, 1_250e6);
        assertEq(invoice, "INV-1042");
        assertEq(note, "October retainer");
    }

    function test_onlyOwnerRegistersPayers() public {
        seal.setPayer(studio, "Kumo Studio");
        assertEq(seal.payerName(studio), "Kumo Studio");
        vm.prank(studio);
        vm.expectRevert(TegamiSeal.NotOwner.selector);
        seal.setPayer(studio, "Fake Bank");
    }

    function test_rejectsPaymentWithoutFunds() public {
        vm.prank(freelancer);
        vm.expectRevert(TegamiSeal.TransferFailed.selector);
        seal.pay(address(usd), studio, 1e6, "", "");
    }

    function test_rejectsOversizedNote() public {
        bytes memory long = new bytes(281);
        vm.prank(studio);
        vm.expectRevert(TegamiSeal.BadInput.selector);
        seal.pay(address(usd), freelancer, 1e6, "", string(long));
    }
}
