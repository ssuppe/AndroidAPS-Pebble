package app.aaps.plugins.pebble.data

data class EnrichedData(
    val bg: Double?,
    val trend: Int?,
    val iob: Double?,
    val cob: Double?,
    val time: Long
)
