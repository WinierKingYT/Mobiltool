package com.personaltool.core.common.time

interface TimeProvider {
    fun currentTimeMillis(): Long
    fun nanoTime(): Long
}

class SystemTimeProvider : TimeProvider {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
    override fun nanoTime(): Long = System.nanoTime()
}
