package app.timeline.agentique.core

import java.text.NumberFormat
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Parses user-typed numbers, accepting Arabic-Indic digits and Arabic separators. */
fun parseNum(s: String): Double? =
    s.map { c -> if (c in '٠'..'٩') '0' + (c - '٠') else c }
        .joinToString("")
        .replace('٫', '.')
        .replace('،', '.')
        .replace(',', '.')
        .trim()
        .toDoubleOrNull()

fun formatNum(x: Double): String =
    NumberFormat.getNumberInstance(AppLocale.locale()).apply { maximumFractionDigits = 2 }.format(x)

fun ZonedDateTime.clock(): String = DateTimeFormatter.ofPattern("h:mm a", AppLocale.locale()).format(this)
