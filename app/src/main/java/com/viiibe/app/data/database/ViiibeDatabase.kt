package com.viiibe.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.viiibe.app.arcade.p2p.P2PDao
import com.viiibe.app.arcade.p2p.P2PSessionEntity
import com.viiibe.app.arcade.p2p.SpectatorBetEntity
import com.viiibe.app.arcade.p2p.WagerEntity
import com.viiibe.app.blockchain.Achievement
import com.viiibe.app.blockchain.BlockchainDao
import com.viiibe.app.blockchain.OnChainWorkout
import com.viiibe.app.blockchain.Wallet
import com.viiibe.app.data.model.ArcadeGameEntity
import com.viiibe.app.data.model.CachedGameHistory
import com.viiibe.app.data.model.CachedPlayerProfile
import com.viiibe.app.data.model.User
import com.viiibe.app.data.model.Workout
import com.viiibe.app.data.model.WorkoutSample

@Database(
    entities = [
        User::class,
        Workout::class,
        WorkoutSample::class,
        Wallet::class,
        Achievement::class,
        OnChainWorkout::class,
        P2PSessionEntity::class,
        WagerEntity::class,
        SpectatorBetEntity::class,
        CachedPlayerProfile::class,
        CachedGameHistory::class,
        ArcadeGameEntity::class
    ],
    version = 6,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class ViiibeDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun blockchainDao(): BlockchainDao
    abstract fun p2pDao(): P2PDao
    abstract fun playerDao(): PlayerDao
    abstract fun arcadeGameDao(): ArcadeGameDao

    companion object {
        @Volatile
        private var INSTANCE: ViiibeDatabase? = null

        // Migration from version 1 to 2 (adding users table)
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create users table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS users (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        avatarColor INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL DEFAULT 0,
                        isActive INTEGER NOT NULL DEFAULT 0,
                        useMetricUnits INTEGER NOT NULL DEFAULT 0,
                        showHeartRateZones INTEGER NOT NULL DEFAULT 1,
                        maxHeartRate INTEGER NOT NULL DEFAULT 220,
                        ftpWatts INTEGER NOT NULL DEFAULT 200
                    )
                """)

                // Create a default user
                database.execSQL("""
                    INSERT INTO users (name, avatarColor, createdAt, isActive)
                    VALUES ('Default', 0, ${System.currentTimeMillis()}, 1)
                """)

                // SQLite doesn't support adding foreign keys via ALTER TABLE
                // We need to recreate the workouts table with the foreign key

                // 1. Create new table with foreign key
                database.execSQL("""
                    CREATE TABLE workouts_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        userId INTEGER NOT NULL DEFAULT 1,
                        startTime INTEGER NOT NULL,
                        endTime INTEGER,
                        durationSeconds INTEGER NOT NULL DEFAULT 0,
                        totalOutput INTEGER NOT NULL DEFAULT 0,
                        avgCadence INTEGER NOT NULL DEFAULT 0,
                        avgResistance INTEGER NOT NULL DEFAULT 0,
                        avgSpeed REAL NOT NULL DEFAULT 0,
                        avgHeartRate INTEGER NOT NULL DEFAULT 0,
                        maxHeartRate INTEGER NOT NULL DEFAULT 0,
                        maxCadence INTEGER NOT NULL DEFAULT 0,
                        maxOutput INTEGER NOT NULL DEFAULT 0,
                        totalDistance REAL NOT NULL DEFAULT 0,
                        caloriesBurned INTEGER NOT NULL DEFAULT 0,
                        workoutType TEXT NOT NULL DEFAULT 'free_ride',
                        videoTitle TEXT,
                        videoUrl TEXT,
                        FOREIGN KEY (userId) REFERENCES users(id) ON DELETE CASCADE
                    )
                """)

                // 2. Copy data from old table
                database.execSQL("""
                    INSERT INTO workouts_new (id, userId, startTime, endTime, durationSeconds, totalOutput,
                        avgCadence, avgResistance, avgSpeed, avgHeartRate, maxHeartRate, maxCadence,
                        maxOutput, totalDistance, caloriesBurned, workoutType, videoTitle, videoUrl)
                    SELECT id, 1, startTime, endTime, durationSeconds, totalOutput,
                        avgCadence, avgResistance, avgSpeed, avgHeartRate, maxHeartRate, maxCadence,
                        maxOutput, totalDistance, caloriesBurned, workoutType, videoTitle, videoUrl
                    FROM workouts
                """)

                // 3. Drop old table
                database.execSQL("DROP TABLE workouts")

                // 4. Rename new table
                database.execSQL("ALTER TABLE workouts_new RENAME TO workouts")

                // 5. Create index
                database.execSQL("CREATE INDEX IF NOT EXISTS index_workouts_userId ON workouts (userId)")
            }
        }

        // Migration from version 2 to 3 (adding blockchain tables)
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create wallets table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS wallets (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        userId INTEGER NOT NULL,
                        address TEXT NOT NULL,
                        encryptedPrivateKey TEXT,
                        isImported INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL DEFAULT 0,
                        network TEXT NOT NULL DEFAULT 'AVALANCHE_MAINNET',
                        FOREIGN KEY (userId) REFERENCES users(id) ON DELETE CASCADE
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_wallets_userId ON wallets (userId)")

                // Create achievements table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS achievements (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        userId INTEGER NOT NULL,
                        type TEXT NOT NULL,
                        earnedAt INTEGER NOT NULL DEFAULT 0,
                        isMinted INTEGER NOT NULL DEFAULT 0,
                        mintTransactionHash TEXT,
                        tokenId TEXT,
                        network TEXT,
                        FOREIGN KEY (userId) REFERENCES users(id) ON DELETE CASCADE
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_achievements_userId ON achievements (userId)")

                // Create onchain_workouts table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS onchain_workouts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        userId INTEGER NOT NULL,
                        workoutId INTEGER NOT NULL,
                        workoutHash TEXT NOT NULL,
                        transactionHash TEXT NOT NULL,
                        network TEXT NOT NULL,
                        recordedAt INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (userId) REFERENCES users(id) ON DELETE CASCADE
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_onchain_workouts_userId ON onchain_workouts (userId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_onchain_workouts_workoutId ON onchain_workouts (workoutId)")
            }
        }

        // Migration from version 3 to 4 (adding P2P and wager tables)
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create p2p_sessions table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS p2p_sessions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        userId INTEGER NOT NULL,
                        sessionId TEXT NOT NULL,
                        gameType TEXT NOT NULL,
                        hostAddress TEXT NOT NULL,
                        guestAddress TEXT,
                        stake TEXT NOT NULL,
                        status TEXT NOT NULL,
                        winner TEXT,
                        hostScore INTEGER NOT NULL DEFAULT 0,
                        guestScore INTEGER NOT NULL DEFAULT 0,
                        stakeTxHash TEXT,
                        settleTxHash TEXT,
                        createdAt INTEGER NOT NULL DEFAULT 0,
                        settledAt INTEGER,
                        FOREIGN KEY (userId) REFERENCES users(id) ON DELETE CASCADE
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_p2p_sessions_userId ON p2p_sessions (userId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_p2p_sessions_sessionId ON p2p_sessions (sessionId)")

                // Create wagers table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS wagers (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        userId INTEGER NOT NULL,
                        gameId TEXT NOT NULL,
                        sessionId TEXT,
                        myAddress TEXT NOT NULL,
                        opponentAddress TEXT,
                        myStake TEXT NOT NULL,
                        opponentStake TEXT,
                        gameType TEXT NOT NULL,
                        result TEXT NOT NULL,
                        payout TEXT,
                        stakeTxHash TEXT,
                        settleTxHash TEXT,
                        createdAt INTEGER NOT NULL DEFAULT 0,
                        settledAt INTEGER,
                        FOREIGN KEY (userId) REFERENCES users(id) ON DELETE CASCADE
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_wagers_userId ON wagers (userId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_wagers_gameId ON wagers (gameId)")

                // Create spectator_bets table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS spectator_bets (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        userId INTEGER NOT NULL,
                        gameId TEXT NOT NULL,
                        predictedWinner TEXT NOT NULL,
                        amount TEXT NOT NULL,
                        odds REAL NOT NULL,
                        result TEXT NOT NULL,
                        payout TEXT,
                        betTxHash TEXT,
                        claimTxHash TEXT,
                        createdAt INTEGER NOT NULL DEFAULT 0,
                        claimedAt INTEGER,
                        FOREIGN KEY (userId) REFERENCES users(id) ON DELETE CASCADE
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_spectator_bets_userId ON spectator_bets (userId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_spectator_bets_gameId ON spectator_bets (gameId)")
            }
        }

        // Migration from version 4 to 5 (adding player profile and game history caching)
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create player_profiles table for caching
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS player_profiles (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        userId INTEGER NOT NULL,
                        walletAddress TEXT NOT NULL,
                        xUsername TEXT,
                        xProfilePicture TEXT,
                        totalGames INTEGER NOT NULL DEFAULT 0,
                        wins INTEGER NOT NULL DEFAULT 0,
                        losses INTEGER NOT NULL DEFAULT 0,
                        winRate REAL NOT NULL DEFAULT 0,
                        totalEarnings REAL NOT NULL DEFAULT 0,
                        totalWagered REAL NOT NULL DEFAULT 0,
                        currentStreak INTEGER NOT NULL DEFAULT 0,
                        bestStreak INTEGER NOT NULL DEFAULT 0,
                        favoriteGame TEXT,
                        rank INTEGER,
                        joinedAt TEXT,
                        lastSyncedAt INTEGER NOT NULL DEFAULT 0,
                        profilePictureRefreshedAt INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (userId) REFERENCES users(id) ON DELETE CASCADE
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_player_profiles_userId ON player_profiles (userId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_player_profiles_walletAddress ON player_profiles (walletAddress)")

                // Create game_history table for caching
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS game_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        userId INTEGER NOT NULL,
                        gameId TEXT NOT NULL,
                        gameType TEXT NOT NULL,
                        gameMode TEXT NOT NULL,
                        hostAddress TEXT NOT NULL,
                        guestAddress TEXT,
                        hostScore INTEGER NOT NULL DEFAULT 0,
                        guestScore INTEGER,
                        winnerAddress TEXT,
                        stakeAmount REAL,
                        durationMs INTEGER,
                        startedAt INTEGER,
                        endedAt INTEGER,
                        createdAt INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (userId) REFERENCES users(id) ON DELETE CASCADE
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_game_history_userId ON game_history (userId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_game_history_gameId ON game_history (gameId)")
            }
        }

        // Migration from version 5 to 6 (adding arcade games table and workout sync fields)
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create arcade_games table for local arcade game history
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS arcade_games (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        userId INTEGER NOT NULL,
                        gameType TEXT NOT NULL,
                        difficulty TEXT NOT NULL,
                        score INTEGER NOT NULL,
                        won INTEGER NOT NULL DEFAULT 0,
                        durationSeconds INTEGER NOT NULL,
                        details TEXT NOT NULL,
                        playedAt INTEGER NOT NULL,
                        synced INTEGER NOT NULL DEFAULT 0,
                        syncedAt INTEGER,
                        FOREIGN KEY (userId) REFERENCES users(id) ON DELETE CASCADE
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_arcade_games_userId ON arcade_games (userId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_arcade_games_synced ON arcade_games (synced)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_arcade_games_playedAt ON arcade_games (playedAt)")

                // Add synced column to workouts table
                database.execSQL("ALTER TABLE workouts ADD COLUMN synced INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE workouts ADD COLUMN syncedAt INTEGER")
            }
        }

        fun getDatabase(context: Context): ViiibeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ViiibeDatabase::class.java,
                    "viiibe_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
