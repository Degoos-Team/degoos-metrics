package com.degoos.metrics.collectors

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Reads the resident set and the container memory. They come from `/proc/self/status` and
 * cgroup v2 on Linux only; elsewhere, or when a file is missing or unreadable, the value is unknown (null) and the
 * metric is simply not emitted. A failed read never throws.
 */
internal object ProcessMemoryReader {
    private const val MAX_FILE_BYTES = 16 * 1024
    private const val KIB = 1024L
    private val STATUS = Path.of("/proc/self/status")
    private val CONTAINER_USED = Path.of("/sys/fs/cgroup/memory.current")
    private val CONTAINER_LIMIT = Path.of("/sys/fs/cgroup/memory.max")

    private val isLinux: Boolean get() = System.getProperty("os.name", "").startsWith("Linux", ignoreCase = true)

    fun residentBytes(): Long? = read(STATUS)?.let(::parseResident)

    fun containerBytes(): Long? = read(CONTAINER_USED)?.let(::parseBytes)

    /** The cgroup memory limit; null when there is none (`max`) or it cannot be read. */
    fun containerLimitBytes(): Long? = read(CONTAINER_LIMIT)?.let(::parseBytes)

    /** The `VmRSS` line of `/proc/self/status` (in kB) as bytes, or null. */
    fun parseResident(status: String): Long? = status.lineSequence()
        .firstOrNull { it.startsWith("VmRSS:") }
        ?.let { Regex("\\d+").find(it)?.value?.toLongOrNull() }
        ?.let { it * KIB }

    /** A cgroup byte count; `max` and anything that is not a non-negative number give null. */
    fun parseBytes(text: String): Long? = text.trim().toLongOrNull()?.takeIf { it >= 0 }

    private fun read(path: Path): String? {
        if (!isLinux) return null
        return try {
            Files.newInputStream(path).use { String(it.readNBytes(MAX_FILE_BYTES), StandardCharsets.UTF_8) }
        } catch (e: Exception) {
            null
        }
    }
}
