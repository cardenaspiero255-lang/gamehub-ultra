package com.cardenaspiero255.gamehubultra.vision

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Bounded read: supports content providers without a declared stream length. */
internal object UltraBoundedImageBytes {
    private const val MAX_BYTES = 8 * 1024 * 1024

    fun read(source: InputStream, limit: Int = MAX_BYTES): ByteArray {
        require(limit in 1..MAX_BYTES) { "Invalid image byte budget" }
        val output = ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        var consecutiveEmptyReads = 0
        while (true) {
            val n = source.read(chunk)
            if (n < 0) break
            if (n == 0) {
                consecutiveEmptyReads++
                require(consecutiveEmptyReads < 8) { "Image provider is not producing data" }
                continue
            }
            consecutiveEmptyReads = 0
            require(n <= limit - output.size()) { "Image exceeds the 8 MiB limit" }
            output.write(chunk, 0, n)
        }
        require(output.size() > 0) { "Selected image is empty" }
        return output.toByteArray()
    }
}
