package com.vinplay.m3u.data.model

/** Result of a link-reachability probe for a channel. */
enum class TestStatus {
    UNTESTED,
    TESTING,
    OK,       // 2xx / playable
    REDIRECT, // 3xx
    DEAD,     // 4xx / 5xx
    TIMEOUT,
    ERROR
}
