package app.aaps.plugins.pebble.data

data class EnrichedData(
    val bg: Double?,
    val trend: Int?,
    val time: Long,
    val iob: String? = null,
    val cob: String? = null,
    val basal: String? = null,
    val iobDetail: String? = null,
    val delta: String? = null,
    val avgDelta: String? = null,
    val history: ByteArray? = null,
    val lowTarget: Int? = null,
    val highTarget: Int? = null,
    val units: Int? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EnrichedData

        if (bg != other.bg) return false
        if (trend != other.trend) return false
        if (time != other.time) return false
        if (iob != other.iob) return false
        if (cob != other.cob) return false
        if (basal != other.basal) return false
        if (iobDetail != other.iobDetail) return false
        if (delta != other.delta) return false
        if (avgDelta != other.avgDelta) return false
        if (history != null) {
            if (other.history == null) return false
            if (!history.contentEquals(other.history)) return false
        } else if (other.history != null) return false
        if (lowTarget != other.lowTarget) return false
        if (highTarget != other.highTarget) return false
        if (units != other.units) return false

        return true
    }

    override fun hashCode(): Int {
        var result = bg?.hashCode() ?: 0
        result = 31 * result + (trend ?: 0)
        result = 31 * result + time.hashCode()
        result = 31 * result + (iob?.hashCode() ?: 0)
        result = 31 * result + (cob?.hashCode() ?: 0)
        result = 31 * result + (basal?.hashCode() ?: 0)
        result = 31 * result + (iobDetail?.hashCode() ?: 0)
        result = 31 * result + (delta?.hashCode() ?: 0)
        result = 31 * result + (avgDelta?.hashCode() ?: 0)
        result = 31 * result + (history?.contentHashCode() ?: 0)
        result = 31 * result + (lowTarget ?: 0)
        result = 31 * result + (highTarget ?: 0)
        result = 31 * result + (units ?: 0)
        return result
    }
}
