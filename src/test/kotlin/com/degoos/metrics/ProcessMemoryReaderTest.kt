package com.degoos.metrics

import com.degoos.metrics.collectors.ProcessMemoryReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProcessMemoryReaderTest {
    @Test
    fun `parses VmRSS in kilobytes`() {
        assertEquals(2048L * 1024, ProcessMemoryReader.parseResident("Name:\tjava\nVmRSS:\t    2048 kB\nThreads:\t5\n"))
        assertNull(ProcessMemoryReader.parseResident("Name:\tjava\n"))
    }

    @Test
    fun `parses cgroup byte counts and treats max as unlimited`() {
        assertEquals(1234L, ProcessMemoryReader.parseBytes("1234\n"))
        assertNull(ProcessMemoryReader.parseBytes("max\n"))
        assertNull(ProcessMemoryReader.parseBytes("-5"))
    }
}
