// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import "@openzeppelin/contracts/token/ERC20/utils/SafeERC20.sol";
import "@openzeppelin/contracts/access/Ownable.sol";
import "@openzeppelin/contracts/utils/ReentrancyGuard.sol";

/**
 * @title ViiibeCore
 * @dev Core wagering and betting contract for the Viiibe fitness gaming platform
 *
 * Features:
 * - Player-to-player wagers on fitness arcade games (WAGERED mode)
 * - Friendly games where only spectators bet (FRIENDLY mode)
 * - Spectator betting pools with dynamic odds
 * - Player revenue sharing from spectator bets in friendly games
 * - Automated settlement with proof verification
 * - Dispute resolution with timeout mechanism
 */
contract ViiibeCore is Ownable, ReentrancyGuard {
    using SafeERC20 for IERC20;

    // ============ State Variables ============

    IERC20 public immutable viiibeToken;

    uint256 public constant PLAYER_FEE_BPS = 500;           // 5% house fee on player wagers
    uint256 public constant SPECTATOR_FEE_BPS = 300;        // 3% total fee on spectator bets
    uint256 public constant PLAYER_REVENUE_BPS = 150;       // 1.5% goes to players in friendly games
    uint256 public constant BPS_DENOMINATOR = 10000;
    uint256 public constant DISPUTE_WINDOW = 5 minutes;
    uint256 public constant MIN_STAKE = 1e18;               // 1 VIIIBE minimum

    uint256 public gameCounter;
    uint256 public totalFeesCollected;

    // Game mode enum
    enum GameMode {
        Wagered,    // 0: Traditional - players stake VIIIBE
        Friendly    // 1: No player stakes - spectators bet, players earn fees
    }

    // Game status enum
    enum GameStatus {
        Created,    // 0: Host created, waiting for guest
        Matched,    // 1: Guest joined, ready to play
        Playing,    // 2: Game in progress
        Ended,      // 3: Game ended, settlement pending
        Settled,    // 4: Payouts complete
        Disputed,   // 5: Under dispute
        Cancelled   // 6: Game cancelled
    }

    struct Game {
        uint256 gameId;
        bytes32 gameType;
        address host;
        address guest;
        uint256 stake;              // 0 for friendly games
        GameMode mode;
        GameStatus status;
        address winner;
        uint256 createdAt;
        uint256 endedAt;
        bytes32 gameHash;           // Hash of game data for verification
        uint256 playerRevenue;      // Accumulated revenue for players (friendly games)
        bool hostClaimedRevenue;
        bool guestClaimedRevenue;
    }

    struct SpectatorBet {
        address bettor;
        address predictedWinner;
        uint256 amount;
        bool claimed;
    }

    struct BettingPool {
        uint256 poolA;          // Total bets on host
        uint256 poolB;          // Total bets on guest
        bool bettingClosed;
    }

    // Mappings
    mapping(uint256 => Game) public games;
    mapping(uint256 => BettingPool) public bettingPools;
    mapping(uint256 => SpectatorBet[]) public spectatorBets;
    mapping(uint256 => mapping(address => uint256)) public betAmounts;
    mapping(uint256 => mapping(address => address)) public betPredictions;

    // ============ Events ============

    event GameCreated(
        uint256 indexed gameId,
        address indexed host,
        bytes32 gameType,
        uint256 stake,
        GameMode mode
    );

    event GameJoined(
        uint256 indexed gameId,
        address indexed guest
    );

    event GameStarted(uint256 indexed gameId);

    event GameEnded(
        uint256 indexed gameId,
        address indexed winner,
        uint256 hostScore,
        uint256 guestScore,
        bytes32 gameHash
    );

    event GameSettled(
        uint256 indexed gameId,
        address indexed winner,
        uint256 payout
    );

    event GameDisputed(
        uint256 indexed gameId,
        address indexed disputer
    );

    event GameCancelled(uint256 indexed gameId);

    event BetPlaced(
        uint256 indexed gameId,
        address indexed bettor,
        address predictedWinner,
        uint256 amount
    );

    event WinningsClaimed(
        uint256 indexed gameId,
        address indexed bettor,
        uint256 payout
    );

    event PlayerRevenueClaimed(
        uint256 indexed gameId,
        address indexed player,
        uint256 amount
    );

    event FeesWithdrawn(address indexed to, uint256 amount);

    // ============ Constructor ============

    constructor(address _viiibeToken) Ownable(msg.sender) {
        require(_viiibeToken != address(0), "Invalid token address");
        viiibeToken = IERC20(_viiibeToken);
    }

    // ============ Player Wager Functions ============

    /**
     * @dev Create a new wagered game (players stake VIIIBE)
     * @param stake Amount of VIIIBE to stake
     * @param gameType Identifier for the game type (e.g., keccak256("SPRINT_RACE"))
     * @return gameId The ID of the created game
     */
    function createGame(
        uint256 stake,
        bytes32 gameType
    ) external nonReentrant returns (uint256 gameId) {
        require(stake >= MIN_STAKE, "Stake below minimum");

        gameId = ++gameCounter;

        games[gameId] = Game({
            gameId: gameId,
            gameType: gameType,
            host: msg.sender,
            guest: address(0),
            stake: stake,
            mode: GameMode.Wagered,
            status: GameStatus.Created,
            winner: address(0),
            createdAt: block.timestamp,
            endedAt: 0,
            gameHash: bytes32(0),
            playerRevenue: 0,
            hostClaimedRevenue: false,
            guestClaimedRevenue: false
        });

        // Transfer stake from host
        viiibeToken.safeTransferFrom(msg.sender, address(this), stake);

        emit GameCreated(gameId, msg.sender, gameType, stake, GameMode.Wagered);
    }

    /**
     * @dev Create a new friendly game (no player stakes, spectators bet)
     * @param gameType Identifier for the game type
     * @return gameId The ID of the created game
     */
    function createFriendlyGame(
        bytes32 gameType
    ) external returns (uint256 gameId) {
        gameId = ++gameCounter;

        games[gameId] = Game({
            gameId: gameId,
            gameType: gameType,
            host: msg.sender,
            guest: address(0),
            stake: 0,
            mode: GameMode.Friendly,
            status: GameStatus.Created,
            winner: address(0),
            createdAt: block.timestamp,
            endedAt: 0,
            gameHash: bytes32(0),
            playerRevenue: 0,
            hostClaimedRevenue: false,
            guestClaimedRevenue: false
        });

        emit GameCreated(gameId, msg.sender, gameType, 0, GameMode.Friendly);
    }

    /**
     * @dev Join an existing game as guest
     * @param gameId The game to join
     */
    function joinGame(uint256 gameId) external nonReentrant {
        Game storage game = games[gameId];
        require(game.status == GameStatus.Created, "Game not available");
        require(game.host != msg.sender, "Cannot join own game");

        game.guest = msg.sender;
        game.status = GameStatus.Matched;

        // Only transfer stake for wagered games
        if (game.mode == GameMode.Wagered) {
            viiibeToken.safeTransferFrom(msg.sender, address(this), game.stake);
        }

        emit GameJoined(gameId, msg.sender);
    }

    /**
     * @dev Mark game as started (called by authorized backend)
     * @param gameId The game to start
     */
    function startGame(uint256 gameId) external onlyOwner {
        Game storage game = games[gameId];
        require(game.status == GameStatus.Matched, "Game not ready");

        game.status = GameStatus.Playing;

        // Close betting when game starts
        bettingPools[gameId].bettingClosed = true;

        emit GameStarted(gameId);
    }

    /**
     * @dev Settle a game with the winner
     * @param gameId The game to settle
     * @param winner Address of the winner
     * @param proof Proof data (game hash for verification)
     */
    function settleGame(
        uint256 gameId,
        address winner,
        bytes calldata proof
    ) external nonReentrant {
        Game storage game = games[gameId];
        require(
            game.status == GameStatus.Playing ||
            game.status == GameStatus.Ended,
            "Game not settleable"
        );
        require(
            winner == game.host || winner == game.guest || winner == address(0),
            "Invalid winner"
        );
        require(
            msg.sender == game.host || msg.sender == game.guest || msg.sender == owner(),
            "Not authorized"
        );

        game.status = GameStatus.Settled;
        game.winner = winner;
        game.endedAt = block.timestamp;
        game.gameHash = keccak256(proof);

        // Handle payouts based on game mode
        if (game.mode == GameMode.Wagered) {
            _settleWageredGame(game, winner);
        }
        // For friendly games, no player payout needed - players claim revenue separately

        emit GameSettled(gameId, winner, game.mode == GameMode.Wagered ? game.stake * 2 : 0);
    }

    /**
     * @dev Internal: Settle a wagered game
     */
    function _settleWageredGame(Game storage game, address winner) internal {
        uint256 totalPot = game.stake * 2;
        uint256 fee = (totalPot * PLAYER_FEE_BPS) / BPS_DENOMINATOR;
        uint256 payout = totalPot - fee;

        totalFeesCollected += fee;

        if (winner != address(0)) {
            viiibeToken.safeTransfer(winner, payout);
        } else {
            // Tie: return stakes minus small fee
            uint256 returnAmount = game.stake - (fee / 2);
            viiibeToken.safeTransfer(game.host, returnAmount);
            viiibeToken.safeTransfer(game.guest, returnAmount);
        }
    }

    /**
     * @dev Dispute a game result
     * @param gameId The game to dispute
     * @param evidence Evidence for the dispute
     */
    function disputeGame(
        uint256 gameId,
        bytes calldata evidence
    ) external {
        Game storage game = games[gameId];
        require(
            game.status == GameStatus.Ended ||
            game.status == GameStatus.Settled,
            "Cannot dispute"
        );
        require(
            block.timestamp <= game.endedAt + DISPUTE_WINDOW,
            "Dispute window closed"
        );
        require(
            msg.sender == game.host || msg.sender == game.guest,
            "Not a player"
        );

        game.status = GameStatus.Disputed;

        emit GameDisputed(gameId, msg.sender);
    }

    /**
     * @dev Cancel a game (only before guest joins)
     * @param gameId The game to cancel
     */
    function cancelGame(uint256 gameId) external nonReentrant {
        Game storage game = games[gameId];
        require(game.status == GameStatus.Created, "Cannot cancel");
        require(msg.sender == game.host || msg.sender == owner(), "Not authorized");

        game.status = GameStatus.Cancelled;

        // Return stake to host (only for wagered games)
        if (game.mode == GameMode.Wagered && game.stake > 0) {
            viiibeToken.safeTransfer(game.host, game.stake);
        }

        emit GameCancelled(gameId);
    }

    // ============ Spectator Betting Functions ============

    /**
     * @dev Place a bet on a game outcome
     * @param gameId The game to bet on
     * @param predictedWinner Address of the player you're betting on
     * @param amount Amount of VIIIBE to bet
     */
    function placeBet(
        uint256 gameId,
        address predictedWinner,
        uint256 amount
    ) external nonReentrant {
        Game storage game = games[gameId];
        BettingPool storage pool = bettingPools[gameId];

        require(
            game.status == GameStatus.Created ||
            game.status == GameStatus.Matched,
            "Betting closed"
        );
        require(!pool.bettingClosed, "Betting closed");
        require(amount > 0, "Invalid amount");
        require(
            predictedWinner == game.host ||
            (game.guest != address(0) && predictedWinner == game.guest),
            "Invalid prediction"
        );
        require(betAmounts[gameId][msg.sender] == 0, "Already bet");

        // Transfer bet amount
        viiibeToken.safeTransferFrom(msg.sender, address(this), amount);

        // Record bet
        spectatorBets[gameId].push(SpectatorBet({
            bettor: msg.sender,
            predictedWinner: predictedWinner,
            amount: amount,
            claimed: false
        }));

        betAmounts[gameId][msg.sender] = amount;
        betPredictions[gameId][msg.sender] = predictedWinner;

        // Update pool
        if (predictedWinner == game.host) {
            pool.poolA += amount;
        } else {
            pool.poolB += amount;
        }

        emit BetPlaced(gameId, msg.sender, predictedWinner, amount);
    }

    /**
     * @dev Claim winnings from a bet
     * @param gameId The game to claim from
     */
    function claimWinnings(uint256 gameId) external nonReentrant {
        Game storage game = games[gameId];
        require(game.status == GameStatus.Settled, "Game not settled");
        require(betAmounts[gameId][msg.sender] > 0, "No bet found");
        require(betPredictions[gameId][msg.sender] == game.winner, "Did not win");

        BettingPool storage pool = bettingPools[gameId];
        uint256 betAmount = betAmounts[gameId][msg.sender];

        // Calculate winnings based on pool proportions
        uint256 winningPool = game.winner == game.host ? pool.poolA : pool.poolB;
        uint256 losingPool = game.winner == game.host ? pool.poolB : pool.poolA;

        // Payout = original bet + proportional share of losing pool
        uint256 winShare = (betAmount * losingPool) / winningPool;
        uint256 grossPayout = betAmount + winShare;

        // Calculate fees
        uint256 totalFee = (winShare * SPECTATOR_FEE_BPS) / BPS_DENOMINATOR;
        uint256 payout;

        if (game.mode == GameMode.Friendly) {
            // Split fee: 1.5% to platform, 1.5% to players
            uint256 platformFee = (winShare * (SPECTATOR_FEE_BPS - PLAYER_REVENUE_BPS)) / BPS_DENOMINATOR;
            uint256 playerShare = (winShare * PLAYER_REVENUE_BPS) / BPS_DENOMINATOR;

            totalFeesCollected += platformFee;
            game.playerRevenue += playerShare;

            payout = grossPayout - platformFee - playerShare;
        } else {
            // Wagered games: all fee goes to platform
            totalFeesCollected += totalFee;
            payout = grossPayout - totalFee;
        }

        // Mark as claimed
        betAmounts[gameId][msg.sender] = 0;

        // Find and mark the bet as claimed
        SpectatorBet[] storage bets = spectatorBets[gameId];
        for (uint i = 0; i < bets.length; i++) {
            if (bets[i].bettor == msg.sender && !bets[i].claimed) {
                bets[i].claimed = true;
                break;
            }
        }

        viiibeToken.safeTransfer(msg.sender, payout);

        emit WinningsClaimed(gameId, msg.sender, payout);
    }

    /**
     * @dev Claim player revenue from friendly game spectator bets
     * @param gameId The game to claim revenue from
     */
    function claimPlayerRevenue(uint256 gameId) external nonReentrant {
        Game storage game = games[gameId];
        require(game.status == GameStatus.Settled, "Game not settled");
        require(game.mode == GameMode.Friendly, "Not a friendly game");
        require(
            msg.sender == game.host || msg.sender == game.guest,
            "Not a player"
        );
        require(game.playerRevenue > 0, "No revenue to claim");

        bool isHost = msg.sender == game.host;

        if (isHost) {
            require(!game.hostClaimedRevenue, "Already claimed");
            game.hostClaimedRevenue = true;
        } else {
            require(!game.guestClaimedRevenue, "Already claimed");
            game.guestClaimedRevenue = true;
        }

        // Split revenue 50/50 between host and guest
        uint256 share = game.playerRevenue / 2;

        // If this is the last player to claim, give them any remainder
        if ((isHost && game.guestClaimedRevenue) || (!isHost && game.hostClaimedRevenue)) {
            share = game.playerRevenue;
            game.playerRevenue = 0;
        } else {
            game.playerRevenue -= share;
        }

        viiibeToken.safeTransfer(msg.sender, share);

        emit PlayerRevenueClaimed(gameId, msg.sender, share);
    }

    // ============ View Functions ============

    /**
     * @dev Get game state
     */
    function getGameState(uint256 gameId) external view returns (
        uint256 _gameId,
        bytes32 gameType,
        address host,
        address guest,
        uint256 stake,
        uint256 status,
        uint256 mode,
        address winner,
        uint256 poolA,
        uint256 poolB,
        uint256 playerRevenue
    ) {
        Game storage game = games[gameId];
        BettingPool storage pool = bettingPools[gameId];

        return (
            game.gameId,
            game.gameType,
            game.host,
            game.guest,
            game.stake,
            uint256(game.status),
            uint256(game.mode),
            game.winner,
            pool.poolA,
            pool.poolB,
            game.playerRevenue
        );
    }

    /**
     * @dev Get current odds for a game (scaled by 100)
     */
    function getOdds(uint256 gameId) external view returns (uint256 oddsA, uint256 oddsB) {
        BettingPool storage pool = bettingPools[gameId];
        uint256 totalPool = pool.poolA + pool.poolB;

        if (totalPool == 0 || pool.poolA == 0 || pool.poolB == 0) {
            return (100, 100); // Even odds
        }

        // Calculate decimal odds * 100
        // If you bet 1 on A and win, you get (totalPool / poolA)
        oddsA = (totalPool * 100) / pool.poolA;
        oddsB = (totalPool * 100) / pool.poolB;
    }

    /**
     * @dev Get bet amount for an address
     */
    function getBetAmount(uint256 gameId, address bettor) external view returns (uint256) {
        return betAmounts[gameId][bettor];
    }

    /**
     * @dev Check if address has claimed winnings
     */
    function hasClaimed(uint256 gameId, address bettor) external view returns (bool) {
        SpectatorBet[] storage bets = spectatorBets[gameId];
        for (uint i = 0; i < bets.length; i++) {
            if (bets[i].bettor == bettor) {
                return bets[i].claimed;
            }
        }
        return false;
    }

    /**
     * @dev Check if a game is friendly mode
     */
    function isFriendlyGame(uint256 gameId) external view returns (bool) {
        return games[gameId].mode == GameMode.Friendly;
    }

    /**
     * @dev Get player revenue info for a friendly game
     */
    function getPlayerRevenueInfo(uint256 gameId) external view returns (
        uint256 totalRevenue,
        bool hostClaimed,
        bool guestClaimed
    ) {
        Game storage game = games[gameId];
        return (
            game.playerRevenue,
            game.hostClaimedRevenue,
            game.guestClaimedRevenue
        );
    }

    // ============ Admin Functions ============

    /**
     * @dev Withdraw collected fees
     */
    function withdrawFees() external onlyOwner {
        uint256 amount = totalFeesCollected;
        totalFeesCollected = 0;
        viiibeToken.safeTransfer(owner(), amount);
        emit FeesWithdrawn(owner(), amount);
    }

    /**
     * @dev Resolve a disputed game (owner only)
     */
    function resolveDispute(
        uint256 gameId,
        address winner
    ) external onlyOwner nonReentrant {
        Game storage game = games[gameId];
        require(game.status == GameStatus.Disputed, "Not disputed");

        game.status = GameStatus.Settled;
        game.winner = winner;

        if (game.mode == GameMode.Wagered) {
            _settleWageredGame(game, winner);
        }

        emit GameSettled(gameId, winner, game.mode == GameMode.Wagered ? game.stake * 2 : 0);
    }
}
