// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@openzeppelin/contracts/token/ERC20/ERC20.sol";
import "@openzeppelin/contracts/token/ERC20/extensions/ERC20Burnable.sol";
import "@openzeppelin/contracts/access/Ownable.sol";

/**
 * @title ViiibeToken
 * @dev The native ERC20 token for the Viiibe fitness gaming platform
 *
 * Token Details:
 * - Name: Viiibe
 * - Symbol: VIIIBE
 * - Decimals: 18
 * - Initial Supply: 1,000,000,000 VIIIBE (1 billion)
 *
 * Distribution:
 * - 40% Community rewards pool
 * - 25% Development fund
 * - 20% Liquidity provision
 * - 10% Team (vested)
 * - 5% Advisors (vested)
 */
contract ViiibeToken is ERC20, ERC20Burnable, Ownable {

    uint256 public constant INITIAL_SUPPLY = 1_000_000_000 * 10**18; // 1 billion tokens

    // Allocation addresses
    address public communityRewardsPool;
    address public developmentFund;
    address public liquidityPool;
    address public teamVesting;
    address public advisorVesting;

    // Track minted amounts
    bool public initialDistributionComplete;

    event InitialDistribution(
        address communityRewardsPool,
        address developmentFund,
        address liquidityPool,
        address teamVesting,
        address advisorVesting
    );

    constructor() ERC20("Viiibe", "VIIIBE") Ownable(msg.sender) {
        // Mint initial supply to deployer for distribution
        _mint(msg.sender, INITIAL_SUPPLY);
    }

    /**
     * @dev Perform initial token distribution
     * Can only be called once by owner
     */
    function distributeInitialTokens(
        address _communityRewardsPool,
        address _developmentFund,
        address _liquidityPool,
        address _teamVesting,
        address _advisorVesting
    ) external onlyOwner {
        require(!initialDistributionComplete, "Already distributed");
        require(_communityRewardsPool != address(0), "Invalid community address");
        require(_developmentFund != address(0), "Invalid dev address");
        require(_liquidityPool != address(0), "Invalid liquidity address");
        require(_teamVesting != address(0), "Invalid team address");
        require(_advisorVesting != address(0), "Invalid advisor address");

        communityRewardsPool = _communityRewardsPool;
        developmentFund = _developmentFund;
        liquidityPool = _liquidityPool;
        teamVesting = _teamVesting;
        advisorVesting = _advisorVesting;

        // Calculate amounts
        uint256 communityAmount = (INITIAL_SUPPLY * 40) / 100;  // 40%
        uint256 devAmount = (INITIAL_SUPPLY * 25) / 100;        // 25%
        uint256 liquidityAmount = (INITIAL_SUPPLY * 20) / 100;  // 20%
        uint256 teamAmount = (INITIAL_SUPPLY * 10) / 100;       // 10%
        uint256 advisorAmount = (INITIAL_SUPPLY * 5) / 100;     // 5%

        // Transfer tokens
        _transfer(msg.sender, communityRewardsPool, communityAmount);
        _transfer(msg.sender, developmentFund, devAmount);
        _transfer(msg.sender, liquidityPool, liquidityAmount);
        _transfer(msg.sender, teamVesting, teamAmount);
        _transfer(msg.sender, advisorVesting, advisorAmount);

        initialDistributionComplete = true;

        emit InitialDistribution(
            communityRewardsPool,
            developmentFund,
            liquidityPool,
            teamVesting,
            advisorVesting
        );
    }

    /**
     * @dev Returns the number of decimals used
     */
    function decimals() public pure override returns (uint8) {
        return 18;
    }
}
