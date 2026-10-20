package io.github.augustinavicius.kulendar.sync

object SyncPlanner {

    /**
     * Computes the calendar changes that make the KULendar events in a calendar mirror [desired].
     *
     * - Events are matched by key regardless of their time, so a rescheduled lecture keeps its entry.
     * - Every key ends up with at most one event; extra copies are always removed. The copy that is
     *   kept is chosen by server id first, so phones syncing into the same Google calendar agree on it.
     * - An event whose reservation disappeared is removed only if it starts inside [window] and
     *   [allowRemovals] is set. Events outside the window are history and stay untouched.
     * - A title, description or location the user edited is kept; times and status still follow the timetable.
     */
    fun plan(
        desired: Collection<EventSpec>,
        existing: Collection<ExistingEvent>,
        window: SyncWindow,
        allowRemovals: Boolean = true,
    ): SyncPlan {
        val desiredByKey = desired.associateBy { it.key }
        val existingByKey = existing.groupBy { it.key }.mapValues { (_, copies) -> copies.sortedWith(keeperOrder) }
        val inserts = mutableListOf<EventSpec>()
        val updates = mutableListOf<EventUpdate>()
        val deletes = mutableListOf<ExistingEvent>()
        var unchanged = 0

        for ((key, spec) in desiredByKey) {
            val copies = existingByKey[key]
            if (copies.isNullOrEmpty()) {
                inserts += spec
                continue
            }
            val keeper = copies.first()
            deletes += copies.drop(1)
            val target = spec.keepingEditsOf(keeper)
            if (keeper.matches(target)) unchanged++ else updates += EventUpdate(keeper, target)
        }
        for ((key, copies) in existingByKey) {
            if (key in desiredByKey) continue
            if (allowRemovals) {
                val (inWindow, history) = copies.partition { it.startMillis in window }
                deletes += inWindow
                deletes += history.drop(1)
            } else {
                deletes += copies.drop(1)
            }
        }
        return SyncPlan(inserts, updates, deletes, unchanged)
    }

    /**
     * Keeps the fields of [event] that differ from what KULendar last wrote into it. Events without a
     * fingerprint predate edit tracking and only ever held KULendar's values, so they are fully updated.
     */
    internal fun EventSpec.keepingEditsOf(event: ExistingEvent): EventSpec {
        val written = EventMarker.extractFingerprint(event.description) ?: return this
        val wanted = EventMarker.extractFingerprint(description) ?: return this
        val current = EventMarker.fingerprint(event.title, event.description, event.location)
        return copy(
            title = if (current.title != written.title) event.title.orEmpty() else title,
            description = if (current.description != written.description) {
                EventMarker.stamp(event.description.orEmpty(), wanted)
            } else {
                description
            },
            location = if (current.location != written.location) event.location.orEmpty() else location,
        )
    }

    internal fun ExistingEvent.matches(spec: EventSpec): Boolean =
        startMillis == spec.startMillis &&
            endMillis == spec.endMillis &&
            tentative == spec.tentative &&
            title.normalized() == spec.title.normalized() &&
            description.normalized() == spec.description.normalized() &&
            location.normalized() == spec.location.normalized()

    private fun String?.normalized(): String = orEmpty().replace("\r\n", "\n").trim()

    private val keeperOrder = compareBy<ExistingEvent>({ it.syncId == null }, { it.syncId }, { it.id })
}
