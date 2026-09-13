package io.github.augustinavicius.kulendar.system

/** Tracks whether one of the app's activities is on screen. */
object AppVisibility {
    private var startedActivities = 0

    val isVisible: Boolean
        @Synchronized get() = startedActivities > 0

    @Synchronized
    fun onActivityStarted() {
        startedActivities++
    }

    @Synchronized
    fun onActivityStopped() {
        startedActivities = (startedActivities - 1).coerceAtLeast(0)
    }
}
