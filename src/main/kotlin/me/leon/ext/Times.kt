package me.leon.ext

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun times() =
    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_hh-mm-ss")) ?: "unknown"