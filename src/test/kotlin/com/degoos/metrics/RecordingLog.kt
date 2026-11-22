package com.degoos.metrics

class RecordingLog : MetricsLog {
    val infos = mutableListOf<String>()
    val warnings = mutableListOf<String>()

    @Synchronized
    override fun info(message: String) {
        infos += message
    }

    @Synchronized
    override fun warn(message: String) {
        warnings += message
    }
}
