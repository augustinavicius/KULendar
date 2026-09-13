package io.github.augustinavicius.kulendar.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPlannerTest {

    private val window = SyncWindow(startMillis = 1_000, endMillisExclusive = 10_000)

    private fun spec(key: String, start: Long = 2_000, title: String = "Lecture $key") = EventSpec(
        key = key,
        title = title,
        description = "Lecturer: Jonas\n\n${EventMarker.format(key)}",
        location = "319 aud.",
        startMillis = start,
        endMillis = start + 500,
        timeZone = "Europe/Vilnius",
        tentative = false,
    )

    private fun stored(id: Long, spec: EventSpec, syncId: String? = null) = ExistingEvent(
        id = id,
        key = spec.key,
        syncId = syncId,
        title = spec.title,
        description = spec.description,
        location = spec.location,
        startMillis = spec.startMillis,
        endMillis = spec.endMillis,
        tentative = spec.tentative,
    )

    @Test
    fun `new reservations are inserted`() {
        val plan = SyncPlanner.plan(listOf(spec("1"), spec("2")), emptyList(), window)

        assertEquals(listOf("1", "2"), plan.inserts.map { it.key })
        assertTrue(plan.updates.isEmpty() && plan.deletes.isEmpty())
    }

    @Test
    fun `events that already match are left alone`() {
        val plan = SyncPlanner.plan(listOf(spec("1")), listOf(stored(10, spec("1"))), window)

        assertEquals(1, plan.unchanged)
        assertTrue(plan.inserts.isEmpty() && plan.updates.isEmpty() && plan.deletes.isEmpty())
    }

    @Test
    fun `changed title or time updates the existing event`() {
        val existing = listOf(stored(10, spec("1")), stored(11, spec("2")))
        val desired = listOf(spec("1", title = "Renamed"), spec("2", start = 3_000))

        val plan = SyncPlanner.plan(desired, existing, window)

        assertEquals(listOf(10L, 11L), plan.updates.map { it.existing.id })
        assertTrue(plan.inserts.isEmpty() && plan.deletes.isEmpty())
    }

    @Test
    fun `line ending and whitespace differences do not cause updates`() {
        val original = spec("1")
        val roundTripped = stored(10, original).copy(description = original.description.replace("\n", "\r\n") + "  ")

        val plan = SyncPlanner.plan(listOf(original), listOf(roundTripped), window)

        assertEquals(1, plan.unchanged)
    }

    @Test
    fun `a rescheduled lecture that was outside the window is moved, not duplicated`() {
        val plan = SyncPlanner.plan(listOf(spec("1", start = 5_000)), listOf(stored(10, spec("1", start = 500))), window)

        assertEquals(listOf(10L), plan.updates.map { it.existing.id })
        assertTrue(plan.inserts.isEmpty() && plan.deletes.isEmpty())
    }

    @Test
    fun `cancelled lectures are removed inside the window, history outside it is kept`() {
        val existing = listOf(stored(10, spec("cancelled", start = 4_000)), stored(11, spec("old", start = 500)))

        val plan = SyncPlanner.plan(emptyList(), existing, window)

        assertEquals(listOf(10L), plan.deletes.map { it.id })
    }

    @Test
    fun `nothing is removed when the download may be incomplete`() {
        val existing = listOf(stored(10, spec("maybe-missing", start = 4_000)))

        val plan = SyncPlanner.plan(emptyList(), existing, window, allowRemovals = false)

        assertTrue(plan.deletes.isEmpty())
    }

    @Test
    fun `duplicate copies are removed, keeping the uploaded copy`() {
        val existing = listOf(stored(10, spec("1")), stored(11, spec("1"), syncId = "google-event-id"), stored(12, spec("1")))

        val plan = SyncPlanner.plan(listOf(spec("1")), existing, window)

        assertEquals(setOf(10L, 12L), plan.deletes.map { it.id }.toSet())
        assertEquals(1, plan.unchanged)
    }

    @Test
    fun `duplicates are removed from history too, and from pending removals when removals are off`() {
        val history = listOf(stored(10, spec("old", start = 500)), stored(11, spec("old", start = 500)))
        val inWindow = listOf(stored(20, spec("gone", start = 4_000)), stored(21, spec("gone", start = 4_000)))

        assertEquals(listOf(11L), SyncPlanner.plan(emptyList(), history, window).deletes.map { it.id })
        assertEquals(listOf(21L), SyncPlanner.plan(emptyList(), inWindow, window, allowRemovals = false).deletes.map { it.id })
    }

    @Test
    fun `applying a plan makes the next plan empty`() {
        val calendar = mutableMapOf(
            10L to stored(10, spec("1", title = "Old title")),
            11L to stored(11, spec("2")),
            12L to stored(12, spec("2")),
            13L to stored(13, spec("cancelled", start = 6_000)),
        )
        val desired = listOf(spec("1"), spec("2"), spec("3", start = 9_000))

        val first = SyncPlanner.plan(desired, calendar.values, window)
        first.deletes.forEach { calendar.remove(it.id) }
        first.updates.forEach { calendar[it.existing.id] = stored(it.existing.id, it.spec, it.existing.syncId) }
        first.inserts.forEachIndexed { index, it -> calendar[100L + index] = stored(100L + index, it) }
        val second = SyncPlanner.plan(desired, calendar.values, window)

        assertTrue(second.inserts.isEmpty() && second.updates.isEmpty() && second.deletes.isEmpty())
        assertEquals(desired.size, second.unchanged)
        assertEquals(desired.size, calendar.size)
    }
}
