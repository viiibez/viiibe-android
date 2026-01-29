package com.viiibe.app.data.repository

import com.viiibe.app.data.model.WorkoutVideo

/**
 * Repository of free cycling workout videos from various YouTube creators.
 *
 * These are all free, publicly available workout videos that users can ride along with.
 * The app does not host or distribute any video content - it simply provides links to
 * publicly available content on YouTube and other platforms.
 *
 * To add your own videos, simply add them to the appropriate category list below.
 *
 * Popular free workout video sources:
 * - GCN (Global Cycling Network) - https://www.youtube.com/@gcntraining
 * - The Sufferfest (some free content)
 * - Zwift (some free content)
 * - Fulgaz (some free scenic rides)
 * - Various independent fitness YouTubers
 */
object VideoRepository {

    /**
     * Get all available workout videos
     */
    fun getAllWorkouts(): List<WorkoutVideo> {
        return hiitWorkouts + enduranceWorkouts + climbWorkouts +
               intervalWorkouts + scenicRides + beginnerWorkouts
    }

    /**
     * Get workouts by category
     */
    fun getWorkoutsByCategory(category: String): List<WorkoutVideo> {
        return when (category.lowercase()) {
            "hiit" -> hiitWorkouts
            "endurance" -> enduranceWorkouts
            "climb" -> climbWorkouts
            "intervals" -> intervalWorkouts
            "scenic" -> scenicRides
            "beginner" -> beginnerWorkouts
            else -> getAllWorkouts()
        }
    }

    /**
     * HIIT (High Intensity Interval Training) workouts
     * Short, intense sessions with periods of max effort
     */
    private val hiitWorkouts = listOf(
        WorkoutVideo(
            id = "hiit_1",
            title = "30 Minute HIIT Indoor Cycling Workout",
            description = "GCN's high intensity interval training. Clear power zone instructions. Warmup, intervals, cooldown.",
            thumbnailUrl = "https://img.youtube.com/vi/dS0JZspM9-A/hqdefault.jpg",
            videoUrl = "https://www.youtube.com/watch?v=dS0JZspM9-A",
            duration = "30:00",
            instructor = "GCN Training",
            difficulty = "advanced",
            category = "hiit"
        ),
        WorkoutVideo(
            id = "hiit_2",
            title = "20 Minute HIIT Indoor Cycling",
            description = "Quick but intense GCN workout. Perfect for time-crunched training days.",
            thumbnailUrl = "https://img.youtube.com/vi/vq5fLPPHmJU/hqdefault.jpg",
            videoUrl = "https://www.youtube.com/watch?v=vq5fLPPHmJU",
            duration = "20:00",
            instructor = "GCN Training",
            difficulty = "advanced",
            category = "hiit"
        ),
        WorkoutVideo(
            id = "hiit_3",
            title = "45 Minute HIIT Indoor Cycling Workout",
            description = "Long-form HIIT session. Progressive intervals with recovery periods. Full body challenge.",
            thumbnailUrl = "https://img.youtube.com/vi/WE6U2Y0XxmI/hqdefault.jpg",
            videoUrl = "https://www.youtube.com/watch?v=WE6U2Y0XxmI",
            duration = "45:00",
            instructor = "GCN Training",
            difficulty = "advanced",
            category = "hiit"
        ),
        WorkoutVideo(
            id = "hiit_4",
            title = "40 Minute Fat Burning Indoor Cycling",
            description = "Optimized for calorie burn with varied intensity. Clear cadence and effort callouts.",
            thumbnailUrl = "https://img.youtube.com/vi/3uHT0qKvUpo/hqdefault.jpg",
            videoUrl = "https://www.youtube.com/watch?v=3uHT0qKvUpo",
            duration = "40:00",
            instructor = "GCN Training",
            difficulty = "intermediate",
            category = "hiit"
        )
    )

    /**
     * Endurance workouts
     * Longer, steady-state rides to build aerobic base
     */
    private val enduranceWorkouts = listOf(
        WorkoutVideo(
            id = "endurance_1",
            title = "60 Minute Endurance Indoor Cycling",
            description = "GCN's hour-long endurance builder. Steady effort with clear zone guidance. Build your base.",
            thumbnailUrl = "https://img.youtube.com/vi/YAgrWK9GdL8/hqdefault.jpg",
            videoUrl = "https://www.youtube.com/watch?v=YAgrWK9GdL8",
            duration = "60:00",
            instructor = "GCN Training",
            difficulty = "intermediate",
            category = "endurance"
        ),
        WorkoutVideo(
            id = "endurance_2",
            title = "45 Minute Endurance Indoor Cycling",
            description = "Moderate length endurance session. Perfect zone 2 training for aerobic development.",
            thumbnailUrl = "https://img.youtube.com/vi/LWmg8qJ_Ixw/hqdefault.jpg",
            videoUrl = "https://www.youtube.com/watch?v=LWmg8qJ_Ixw",
            duration = "45:00",
            instructor = "GCN Training",
            difficulty = "intermediate",
            category = "endurance"
        ),
        WorkoutVideo(
            id = "endurance_3",
            title = "30 Minute Recovery Ride",
            description = "Easy spinning for active recovery. Low intensity, focus on leg speed not power.",
            thumbnailUrl = "https://img.youtube.com/vi/QCgKJnFp6aA/hqdefault.jpg",
            videoUrl = "https://www.youtube.com/watch?v=QCgKJnFp6aA",
            duration = "30:00",
            instructor = "GCN Training",
            difficulty = "beginner",
            category = "endurance"
        )
    )

    /**
     * Climb workouts
     * High resistance, lower cadence efforts simulating hill climbs
     */
    private val climbWorkouts = listOf(
        WorkoutVideo(
            id = "climb_1",
            title = "30 Minute Climbing Indoor Cycling",
            description = "GCN hill simulation. Seated and standing climbs. Build leg strength and climbing power.",
            thumbnailUrl = "https://img.youtube.com/vi/zFAiW7WLfxo/hqdefault.jpg",
            videoUrl = "https://www.youtube.com/watch?v=zFAiW7WLfxo",
            duration = "30:00",
            instructor = "GCN Training",
            difficulty = "intermediate",
            category = "climb"
        ),
        WorkoutVideo(
            id = "climb_2",
            title = "45 Minute Climbing Indoor Cycling",
            description = "Extended climbing session. Progressive efforts simulating mountain passes.",
            thumbnailUrl = "https://img.youtube.com/vi/Uf9pW-z-c10/hqdefault.jpg",
            videoUrl = "https://www.youtube.com/watch?v=Uf9pW-z-c10",
            duration = "45:00",
            instructor = "GCN Training",
            difficulty = "advanced",
            category = "climb"
        ),
        WorkoutVideo(
            id = "climb_3",
            title = "20 Minute Climbing Workout",
            description = "Quick but intense climbing session. Perfect for building power in limited time.",
            thumbnailUrl = "https://img.youtube.com/vi/ThjPDl-r8tc/hqdefault.jpg",
            videoUrl = "https://www.youtube.com/watch?v=ThjPDl-r8tc",
            duration = "20:00",
            instructor = "GCN Training",
            difficulty = "intermediate",
            category = "climb"
        )
    )

    /**
     * Interval workouts
     * Structured intervals at various intensities
     */
    private val intervalWorkouts = listOf(
        WorkoutVideo(
            id = "intervals_1",
            title = "30 Minute Sweet Spot Intervals",
            description = "GCN threshold training. Build FTP with structured intervals. Clear power targets.",
            thumbnailUrl = "https://img.youtube.com/vi/H9Tf3xJuKaA/hqdefault.jpg",
            videoUrl = "https://www.youtube.com/watch?v=H9Tf3xJuKaA",
            duration = "30:00",
            instructor = "GCN Training",
            difficulty = "intermediate",
            category = "intervals"
        ),
        WorkoutVideo(
            id = "intervals_2",
            title = "40 Minute VO2 Max Intervals",
            description = "High intensity intervals to boost your aerobic ceiling. Hard efforts with full recovery.",
            thumbnailUrl = "https://img.youtube.com/vi/PHLVwFB2SHw/hqdefault.jpg",
            videoUrl = "https://www.youtube.com/watch?v=PHLVwFB2SHw",
            duration = "40:00",
            instructor = "GCN Training",
            difficulty = "advanced",
            category = "intervals"
        ),
        WorkoutVideo(
            id = "intervals_3",
            title = "45 Minute Threshold Intervals",
            description = "Sustained FTP intervals. Build your power at threshold. Essential for racing fitness.",
            thumbnailUrl = "https://img.youtube.com/vi/MIlQPQFP1QA/hqdefault.jpg",
            videoUrl = "https://www.youtube.com/watch?v=MIlQPQFP1QA",
            duration = "45:00",
            instructor = "GCN Training",
            difficulty = "advanced",
            category = "intervals"
        )
    )

    /**
     * Scenic rides
     * Virtual rides through beautiful locations
     */
    private val scenicRides = listOf(
        WorkoutVideo(
            id = "scenic_1",
            title = "Swiss Alps Virtual Ride",
            description = "Beautiful Swiss mountain scenery. 4K footage, ride at your own pace.",
            thumbnailUrl = "https://img.youtube.com/vi/Scxs7L0vhZ4/hqdefault.jpg",
            videoUrl = "https://www.youtube.com/watch?v=Scxs7L0vhZ4",
            duration = "60:00",
            instructor = "Scenic",
            difficulty = "beginner",
            category = "scenic"
        ),
        WorkoutVideo(
            id = "scenic_2",
            title = "Coastal California Ride",
            description = "Stunning coastal views along California. Relaxing virtual cycling experience.",
            thumbnailUrl = "https://img.youtube.com/vi/0xOkszX54EI/hqdefault.jpg",
            videoUrl = "https://www.youtube.com/watch?v=0xOkszX54EI",
            duration = "45:00",
            instructor = "Scenic",
            difficulty = "beginner",
            category = "scenic"
        ),
        WorkoutVideo(
            id = "scenic_3",
            title = "Italian Countryside",
            description = "Ride through beautiful Italian landscapes. Vineyards and rolling hills.",
            thumbnailUrl = "https://img.youtube.com/vi/Q9xDwb2RCJI/hqdefault.jpg",
            videoUrl = "https://www.youtube.com/watch?v=Q9xDwb2RCJI",
            duration = "50:00",
            instructor = "Scenic",
            difficulty = "beginner",
            category = "scenic"
        ),
        WorkoutVideo(
            id = "scenic_4",
            title = "Forest Trail Ride",
            description = "Peaceful ride through lush forest paths. Perfect for relaxed sessions.",
            thumbnailUrl = "https://img.youtube.com/vi/Bey4XXJAqS8/hqdefault.jpg",
            videoUrl = "https://www.youtube.com/watch?v=Bey4XXJAqS8",
            duration = "40:00",
            instructor = "Scenic",
            difficulty = "beginner",
            category = "scenic"
        )
    )

    /**
     * Beginner workouts
     * Lower intensity, clear instructions, great for new riders
     */
    private val beginnerWorkouts = listOf(
        WorkoutVideo(
            id = "beginner_1",
            title = "20 Minute Beginner Indoor Cycling",
            description = "Perfect intro to indoor cycling. GCN guides you through basics with clear instructions.",
            thumbnailUrl = "https://img.youtube.com/vi/OM9mkn3NF9s/hqdefault.jpg",
            videoUrl = "https://www.youtube.com/watch?v=OM9mkn3NF9s",
            duration = "20:00",
            instructor = "GCN Training",
            difficulty = "beginner",
            category = "beginner"
        ),
        WorkoutVideo(
            id = "beginner_2",
            title = "30 Minute Low Intensity Ride",
            description = "Easy-paced workout for fitness building. No prior experience needed.",
            thumbnailUrl = "https://img.youtube.com/vi/QCgKJnFp6aA/hqdefault.jpg",
            videoUrl = "https://www.youtube.com/watch?v=QCgKJnFp6aA",
            duration = "30:00",
            instructor = "GCN Training",
            difficulty = "beginner",
            category = "beginner"
        ),
        WorkoutVideo(
            id = "beginner_3",
            title = "15 Minute Quick Spin",
            description = "Short and effective workout. Perfect when time is limited. Gentle on the body.",
            thumbnailUrl = "https://img.youtube.com/vi/zU1c0wB2kkU/hqdefault.jpg",
            videoUrl = "https://www.youtube.com/watch?v=zU1c0wB2kkU",
            duration = "15:00",
            instructor = "GCN Training",
            difficulty = "beginner",
            category = "beginner"
        ),
        WorkoutVideo(
            id = "beginner_4",
            title = "25 Minute Easy Cycling Workout",
            description = "Comfortable pace throughout. Great for building cycling habit and base fitness.",
            thumbnailUrl = "https://img.youtube.com/vi/vULrWB__RYY/hqdefault.jpg",
            videoUrl = "https://www.youtube.com/watch?v=vULrWB__RYY",
            duration = "25:00",
            instructor = "GCN Training",
            difficulty = "beginner",
            category = "beginner"
        )
    )
}
