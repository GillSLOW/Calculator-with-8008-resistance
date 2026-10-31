package com.hotscan.calc

/** category, formatted line, and whether this line is an anomaly/alert. */
typealias SignalCallback = (category: String, line: String, alert: Boolean) -> Unit

/** Anything the ScanService can switch on and off. */
interface Scanner {
    fun start()
    fun stop()
}
