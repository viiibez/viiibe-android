package com.viiibe.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.viiibe.app.blockchain.Achievement
import com.viiibe.app.blockchain.AchievementType
import com.viiibe.app.blockchain.BlockchainViewModel
import com.viiibe.app.ui.viewmodel.UserViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(
    blockchainViewModel: BlockchainViewModel,
    userViewModel: UserViewModel,
    onBack: () -> Unit
) {
    val walletState by blockchainViewModel.walletState.collectAsState()
    val onChainStats by blockchainViewModel.onChainStats.collectAsState()
    val activeUser by userViewModel.activeUser.collectAsState()

    var selectedTab by remember { mutableStateOf(0) }

    val earnedAchievements = onChainStats.earnedAchievements
    val allAchievements = AchievementType.entries
    val lockedAchievements = allAchievements.filter { type ->
        earnedAchievements.none { it.type == type }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top bar
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = "Achievements",
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${earnedAchievements.size} / ${allAchievements.size} earned",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )

        // Tab row
        TabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Earned (${earnedAchievements.size})") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("All (${allAchievements.size})") }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (selectedTab) {
            0 -> {
                // Earned achievements
                if (earnedAchievements.isEmpty()) {
                    EmptyAchievementsView()
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(earnedAchievements) { achievement ->
                            EarnedAchievementCard(
                                achievement = achievement,
                                walletConnected = walletState.isConnected
                            )
                        }
                    }
                }
            }
            1 -> {
                // All achievements
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(allAchievements.toList()) { type ->
                        val earned = earnedAchievements.find { it.type == type }
                        AchievementCard(
                            type = type,
                            earned = earned,
                            walletConnected = walletState.isConnected
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyAchievementsView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Filled.EmojiEvents,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "No Achievements Yet",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Complete workouts to earn achievements and mint them as NFTs!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EarnedAchievementCard(
    achievement: Achievement,
    walletConnected: Boolean
) {
    val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (achievement.isMinted) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Achievement icon
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary
            ) {
                Box(
                    modifier = Modifier.size(56.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getAchievementIcon(achievement.type),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = achievement.type.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = achievement.type.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Status badge
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (achievement.isMinted) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                } else {
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (achievement.isMinted) Icons.Filled.Verified else Icons.Filled.Stars,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (achievement.isMinted) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.secondary
                        }
                    )
                    Text(
                        text = if (achievement.isMinted) "Minted" else "Earned",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = if (achievement.isMinted) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.secondary
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = dateFormat.format(Date(achievement.earnedAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementCard(
    type: AchievementType,
    earned: Achievement?,
    walletConnected: Boolean
) {
    val isEarned = earned != null
    val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isEarned) 1f else 0.5f),
        shape = RoundedCornerShape(16.dp),
        color = when {
            earned?.isMinted == true -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            isEarned -> MaterialTheme.colorScheme.surfaceVariant
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        }
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Achievement icon
            Surface(
                shape = CircleShape,
                color = if (isEarned) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                }
            ) {
                Box(
                    modifier = Modifier.size(56.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isEarned) {
                            getAchievementIcon(type)
                        } else {
                            Icons.Filled.Lock
                        },
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = if (isEarned) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = type.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = type.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (isEarned && earned != null) {
                // Status badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (earned.isMinted) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    } else {
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (earned.isMinted) Icons.Filled.Verified else Icons.Filled.Stars,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (earned.isMinted) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.secondary
                            }
                        )
                        Text(
                            text = if (earned.isMinted) "Minted" else "Earned",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = if (earned.isMinted) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.secondary
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = dateFormat.format(Date(earned.earnedAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            } else {
                // Locked badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Text(
                            text = "Locked",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Requires: ${type.requiredValue}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun getAchievementIcon(type: AchievementType): ImageVector {
    return when (type) {
        // Ride achievements
        AchievementType.FIRST_RIDE -> Icons.Filled.DirectionsBike
        AchievementType.TEN_RIDES -> Icons.Filled.FitnessCenter
        AchievementType.FIFTY_RIDES -> Icons.Filled.MilitaryTech
        AchievementType.HUNDRED_RIDES -> Icons.Filled.EmojiEvents

        // Distance achievements
        AchievementType.TEN_MILES -> Icons.Filled.Route
        AchievementType.FIFTY_MILES -> Icons.Filled.Map
        AchievementType.HUNDRED_MILES -> Icons.Filled.Public
        AchievementType.FIVE_HUNDRED_MILES -> Icons.Filled.Explore
        AchievementType.THOUSAND_MILES -> Icons.Filled.Star

        // Calorie achievements
        AchievementType.THOUSAND_CALS -> Icons.Filled.LocalFireDepartment
        AchievementType.TEN_K_CALS -> Icons.Filled.Whatshot
        AchievementType.FIFTY_K_CALS -> Icons.Filled.Bolt

        // Time achievements
        AchievementType.HOUR_RIDER -> Icons.Filled.Timer
        AchievementType.TEN_HOURS -> Icons.Filled.Schedule
        AchievementType.FIFTY_HOURS -> Icons.Filled.HourglassFull

        // Output achievements
        AchievementType.HUNDRED_KJ -> Icons.Filled.FlashOn
        AchievementType.THOUSAND_KJ -> Icons.Filled.ElectricBolt
        AchievementType.TEN_K_KJ -> Icons.Filled.OfflineBolt
    }
}
