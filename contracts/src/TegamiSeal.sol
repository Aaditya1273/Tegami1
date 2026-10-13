// SPDX-License-Identifier: MIT
pragma solidity ^0.8.26;

interface IERC20 {
    function transferFrom(address from, address to, uint256 amount) external returns (bool);
}

/// @title TegamiSeal
/// @notice Pays a stablecoin (AUSD on mainnet, a test dollar on testnet) and records a "letter" for the
/// recipient in the same transaction: who paid, how much, when, the invoice reference and the payer's note.
///
/// What a letter proves: `payer` is `msg.sender`, so the payer signed this payment, and the invoice was
/// attached by that payer in the same transaction. What it does not prove: where the payer's money came from.
/// The registry is a list kept by `owner`; being listed is not a check on the source of funds.
contract TegamiSeal {
    struct Letter {
        address token;
        address payer;
        uint128 amount;
        uint64 time;
        uint64 blockNumber;
        string invoice;
        string note;
    }

    address public immutable owner;
    /// Registered payers by display name; an empty name means not registered.
    mapping(address => string) public payerName;
    mapping(address => Letter[]) private letters;

    event Sealed(address indexed to, uint256 indexed index, address indexed payer, address token, uint256 amount, string invoice);
    event PayerSet(address indexed payer, string name);

    error NotOwner();
    error BadInput();
    error TransferFailed();

    constructor() {
        owner = msg.sender;
    }

    function setPayer(address payer, string calldata name) external {
        if (msg.sender != owner) revert NotOwner();
        if (bytes(name).length > 64) revert BadInput();
        payerName[payer] = name;
        emit PayerSet(payer, name);
    }

    /// @notice Moves `amount` of `token` from the caller to `to` (needs an allowance) and records the letter.
    /// @return index The letter's position in the recipient's list, its permanent id together with `to`.
    function pay(address token, address to, uint256 amount, string calldata invoice, string calldata note)
        external
        returns (uint256 index)
    {
        if (to == address(0) || amount == 0 || amount > type(uint128).max) revert BadInput();
        if (bytes(invoice).length > 64 || bytes(note).length > 280) revert BadInput();
        // Low-level call so tokens that return nothing (non-standard ERC-20s) work too.
        (bool ok, bytes memory ret) = token.call(abi.encodeCall(IERC20.transferFrom, (msg.sender, to, amount)));
        if (!ok || (ret.length != 0 && !abi.decode(ret, (bool)))) revert TransferFailed();
        letters[to].push(Letter(token, msg.sender, uint128(amount), uint64(block.timestamp), uint64(block.number), invoice, note));
        index = letters[to].length - 1;
        emit Sealed(to, index, msg.sender, token, amount, invoice);
    }

    function letterCount(address to) external view returns (uint256) {
        return letters[to].length;
    }

    function letterAt(address to, uint256 index)
        external
        view
        returns (address token, address payer, uint256 amount, uint64 time, uint64 blockNumber, string memory invoice, string memory note)
    {
        Letter storage l = letters[to][index];
        return (l.token, l.payer, l.amount, l.time, l.blockNumber, l.invoice, l.note);
    }
}
