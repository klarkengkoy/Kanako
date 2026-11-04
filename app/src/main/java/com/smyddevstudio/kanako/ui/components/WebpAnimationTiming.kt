package com.smyddevstudio.kanako.ui.components

internal fun retimeAnimatedWebp(source: ByteArray, durationMillis: Int): ByteArray {
    require(durationMillis in 1..0xFFFFFF)
    require(source.size >= 12 && source.matchesAscii(0, "RIFF") && source.matchesAscii(8, "WEBP"))
    require(source.readUnsigned(4, 4) + 8L == source.size.toLong())
    val offsets = mutableListOf<Int>()
    var position = 12
    while (position < source.size) {
        require(position + 8 <= source.size)
        val length = source.readUnsigned(position + 4, 4)
        val end = position.toLong() + 8 + length + (length % 2)
        require(end <= source.size && end > position)
        if (source.matchesAscii(position, "ANMF")) {
            require(length >= 16)
            offsets += position + 20
        }
        position = end.toInt()
    }
    require(offsets.isNotEmpty() && durationMillis >= offsets.size)
    val weights = offsets.map { source.readUnsigned(it, 3).coerceAtLeast(1) }
    val totalWeight = weights.sum()
    val remaining = durationMillis - offsets.size
    val durations = weights.map { 1 + (remaining * it / totalWeight).toInt() }.toMutableList()
    val remainder = durationMillis - durations.sum()
    weights.indices.sortedByDescending { remaining * weights[it] % totalWeight }
        .take(remainder).forEach { durations[it]++ }
    return source.copyOf().also { result ->
        offsets.forEachIndexed { index, offset ->
            repeat(3) { byte -> result[offset + byte] = (durations[index] ushr (byte * 8)).toByte() }
        }
    }
}

private fun ByteArray.matchesAscii(offset: Int, value: String) =
    offset >= 0 && offset + value.length <= size && value.indices.all { this[offset + it].toInt() == value[it].code }

private fun ByteArray.readUnsigned(offset: Int, length: Int): Long =
    (0 until length).fold(0L) { value, byte -> value or ((this[offset + byte].toLong() and 0xFF) shl (byte * 8)) }
