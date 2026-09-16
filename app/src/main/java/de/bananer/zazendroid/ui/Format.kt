package de.bananer.zazendroid.ui

fun formatMs(ms: Long?): String {
    if (ms == null || ms <= 0) return "–"
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}

/** Position clock: 0 is a valid position, only negatives are unknown. */
fun formatPosition(ms: Long): String {
    if (ms < 0) return "–"
    return "%d:%02d".format(ms / 1000 / 60, ms / 1000 % 60)
}
