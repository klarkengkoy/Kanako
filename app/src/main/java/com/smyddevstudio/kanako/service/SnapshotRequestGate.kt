package com.smyddevstudio.kanako.service

internal class SnapshotRequestGate {
    private var generation = 0L
    private var request: Long? = null

    fun begin(): Long {
        generation++
        request = generation
        return generation
    }

    fun invalidate() {
        generation++
        request = null
    }

    fun isCurrent(token: Long): Boolean = request == token && generation == token
}
