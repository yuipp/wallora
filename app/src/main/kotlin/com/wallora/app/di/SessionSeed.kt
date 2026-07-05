package com.wallora.app.di

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue

/**
 * A per-process random seed, fixed for the lifetime of the app process.
 *
 * Used to make the feed feel fresh on each launch without being random on every page load:
 * - [topicOffset] rotates which category/keyword each source queries first, so the very first
 *   screen differs between launches (but stays stable while the user scrolls a session).
 * - [wallhavenSeed] is passed to Wallhaven's `random` sorting on deeper pages so the "more of
 *   the same" pool is shuffled consistently within a session and reshuffled across launches.
 *
 * The seed is derived from [System.nanoTime] at construction. It is deliberately NOT persisted —
 * a new process (relaunch) should get a new mix.
 */
@Singleton
class SessionSeed @Inject constructor() {

    private val value: Long = System.nanoTime()

    /** Non-negative offset added to the per-source topic rotation. */
    val topicOffset: Int = (value and 0x7FFF).toInt().absoluteValue

    /** 6-char alphanumeric seed accepted by Wallhaven's `seed` query param. */
    val wallhavenSeed: String =
        java.lang.Long.toString(value and 0xFFFFFFFFFFL, 36).padStart(6, '0').takeLast(6)
}
