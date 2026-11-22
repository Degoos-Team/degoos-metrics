package com.degoos.metrics

import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function

/**
 * Maps world names to the `world` label value. Plugins that create many short-lived worlds register a grouper so those
 * worlds share one label instead of growing a series per world. Safe to use from any thread.
 */
class WorldGroups(private val log: MetricsLog = NoLog) {
    // Insertion order matters ("first grouper wins"), so this is a synchronized LinkedHashMap, copied before iterating.
    private val groupers = LinkedHashMap<String, Function<String, String?>>()
    private val warned = ConcurrentHashMap.newKeySet<String>()

    /** Registers (or replaces) [owner]'s grouper: a world name in, a group label out, or null to leave the world alone. */
    fun set(owner: String, grouper: Function<String, String?>) {
        require(owner.isNotBlank()) { "A world grouper needs an owner" }
        synchronized(groupers) { groupers[owner] = grouper }
    }

    /** Removes [owner]'s grouper; always safe. */
    fun remove(owner: String) {
        synchronized(groupers) { groupers.remove(owner) }
    }

    /**
     * The label for [worldName]: the first non-empty group from the groupers (in registration order), otherwise the
     * world's own name; always sanitized. A grouper that throws counts as null and is logged once.
     */
    fun label(worldName: String): String {
        val snapshot = synchronized(groupers) { groupers.entries.map { it.key to it.value } }
        for ((owner, grouper) in snapshot) {
            val group = try {
                grouper.apply(worldName)
            } catch (e: Throwable) {
                if (!e.isRecoverable()) throw e
                if (warned.add(owner)) log.warn("World grouper of $owner failed (${e.javaClass.simpleName}: ${e.message}) and is ignored")
                null
            }
            if (!group.isNullOrEmpty()) return LabelRules.sanitizeLabelValue(group)
        }
        return LabelRules.sanitizeLabelValue(worldName)
    }
}
