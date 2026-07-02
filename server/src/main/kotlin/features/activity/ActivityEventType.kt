package com.carspotter.features.activity

/** Milestone-type activity events, persisted at most once per (user, day). */
enum class ActivityEventType {
    STREAK,
    LEADERBOARD_UP,
}
