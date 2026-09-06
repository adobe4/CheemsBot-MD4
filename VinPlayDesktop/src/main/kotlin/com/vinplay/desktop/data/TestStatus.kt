package com.vinplay.desktop.data

/** Result of a link-reachability probe for a channel. */
enum class TestStatus {
    UNTESTED,
    TESTING,
    OK,       // reachable / plays cleanly (green)
    UNSTABLE, // plays then freezes/rebuffers within a few seconds (yellow)
    REDIRECT, // 3xx
    DEAD,     // 4xx / 5xx / unreachable (red)
    TIMEOUT,
    ERROR
}
