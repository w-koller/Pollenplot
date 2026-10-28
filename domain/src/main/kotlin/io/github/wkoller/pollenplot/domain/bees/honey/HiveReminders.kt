package io.github.wkoller.pollenplot.domain.bees.honey

import java.time.LocalDate

/**
 * Which hive checks to send a notification about. A check is told about once, on the morning it falls due or the
 * first morning after. Once it is done its next due date is in the future, so it is forgotten, and it is told about
 * again when that date comes.
 */
object HiveReminders {
    /** What a check is remembered by while it is due: its hive and its kind. */
    fun key(task: HiveTask): String = "${task.hiveId}/${task.kind}"

    /** The checks due by [today] that have not been told about yet, soonest first. */
    fun toTell(tasks: List<HiveTask>, today: LocalDate, told: Set<String>): List<HiveTask> =
        tasks.filter { !it.due.isAfter(today) && key(it) !in told }.sortedBy { it.due }

    /**
     * What to remember once [tellingNow] has been told about: every check still due that was told about before or is
     * now. A check that was done, or one for a hive that is gone, drops out.
     */
    fun remembered(tasks: List<HiveTask>, today: LocalDate, told: Set<String>, tellingNow: List<HiveTask>): Set<String> {
        val due = tasks.filter { !it.due.isAfter(today) }.map(::key).toSet()
        return (told intersect due) + tellingNow.map(::key)
    }
}
