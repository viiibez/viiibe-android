package com.viiibe.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.viiibe.app.ViiibeApplication
import com.viiibe.app.data.model.AvatarColors
import com.viiibe.app.data.model.User
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class UserViewModel(application: Application) : AndroidViewModel(application) {

    private val userDao = ViiibeApplication.instance.database.userDao()
    private val workoutDao = ViiibeApplication.instance.database.workoutDao()

    // All users
    val allUsers: StateFlow<List<User>> = userDao.getAllUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Currently active user
    val activeUser: StateFlow<User?> = userDao.getActiveUser()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // User count for determining if we need to show profile selector
    val userCount: StateFlow<Int> = userDao.getUserCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Check if we need to show the profile selector (no users or no active user)
    val needsProfileSelection: StateFlow<Boolean> = combine(userCount, activeUser) { count, active ->
        count == 0 || active == null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // Stats for active user
    fun getWorkoutCountForUser(userId: Long): Flow<Int> = workoutDao.getTotalWorkoutCountForUser(userId)
    fun getTotalMinutesForUser(userId: Long): Flow<Int?> = workoutDao.getTotalWorkoutSecondsForUser(userId)
    fun getTotalOutputForUser(userId: Long): Flow<Int?> = workoutDao.getTotalOutputForUser(userId)
    fun getTotalCaloriesForUser(userId: Long): Flow<Int?> = workoutDao.getTotalCaloriesForUser(userId)
    fun getTotalDistanceForUser(userId: Long): Flow<Float?> = workoutDao.getTotalDistanceForUser(userId)
    fun getWorkoutsForUser(userId: Long): Flow<List<com.viiibe.app.data.model.Workout>> = workoutDao.getWorkoutsForUser(userId)

    /**
     * Create a new user profile
     */
    fun createUser(name: String, colorIndex: Int = 0, setActive: Boolean = true) {
        viewModelScope.launch {
            val user = User(
                name = name.trim(),
                avatarColor = colorIndex,
                createdAt = System.currentTimeMillis(),
                isActive = false
            )
            val userId = userDao.insertUser(user)

            if (setActive) {
                userDao.setActiveUser(userId)
            }
        }
    }

    /**
     * Switch to a different user profile
     */
    fun switchUser(userId: Long) {
        viewModelScope.launch {
            userDao.setActiveUser(userId)
        }
    }

    /**
     * Update user's name
     */
    fun updateUserName(userId: Long, newName: String) {
        viewModelScope.launch {
            val user = userDao.getUserById(userId)
            user?.let {
                userDao.updateUser(it.copy(name = newName.trim()))
            }
        }
    }

    /**
     * Update user's avatar color
     */
    fun updateUserColor(userId: Long, colorIndex: Int) {
        viewModelScope.launch {
            val user = userDao.getUserById(userId)
            user?.let {
                userDao.updateUser(it.copy(avatarColor = colorIndex))
            }
        }
    }

    /**
     * Update user settings
     */
    fun updateUserSettings(
        userId: Long,
        useMetricUnits: Boolean? = null,
        showHeartRateZones: Boolean? = null,
        maxHeartRate: Int? = null,
        ftpWatts: Int? = null
    ) {
        viewModelScope.launch {
            val user = userDao.getUserById(userId)
            user?.let {
                userDao.updateUser(
                    it.copy(
                        useMetricUnits = useMetricUnits ?: it.useMetricUnits,
                        showHeartRateZones = showHeartRateZones ?: it.showHeartRateZones,
                        maxHeartRate = maxHeartRate ?: it.maxHeartRate,
                        ftpWatts = ftpWatts ?: it.ftpWatts
                    )
                )
            }
        }
    }

    /**
     * Delete a user profile (and their workouts due to cascade)
     */
    fun deleteUser(user: User) {
        viewModelScope.launch {
            userDao.deleteUser(user)

            // If we deleted the active user, activate another one if available
            if (user.isActive) {
                val remainingUsers = userDao.getUserCountOnce()
                if (remainingUsers > 0) {
                    val users = allUsers.value
                    users.firstOrNull()?.let {
                        userDao.setActiveUser(it.id)
                    }
                }
            }
        }
    }

    /**
     * Initialize with a default user if none exist
     */
    fun ensureDefaultUser() {
        viewModelScope.launch {
            val count = userDao.getUserCountOnce()
            if (count == 0) {
                createUser("Rider 1", colorIndex = 0, setActive = true)
            }
        }
    }
}
