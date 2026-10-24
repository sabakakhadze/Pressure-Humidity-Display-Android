package com.portworld.roomindicator

/**
 * A snapshot of the three Modbus registers plus per-register freshness.
 * Mirrors the ESP32 indicator_sample_t: a register not written within the
 * timeout reads back invalid, so the UI shows "---" for it.
 */
data class Sample(
    val humidityX10: Int,
    val pressureX10: Int,
    val counter: Int,
    val online: Boolean,   // any register fresh (comms alive)
    val humValid: Boolean,
    val preValid: Boolean,
    val cntValid: Boolean,
)
