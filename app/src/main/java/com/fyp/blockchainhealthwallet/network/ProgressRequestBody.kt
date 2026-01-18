package com.fyp.blockchainhealthwallet.network

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.File
import kotlin.math.roundToInt

/**
 * RequestBody that supports progress callbacks for large file uploads.
 * This prevents loading entire files into memory.
 */
class ProgressRequestBody(
    private val file: File,
    private val contentType: MediaType?,
    private val progressCallback: ((Int) -> Unit)? = null
) : RequestBody() {

    override fun contentType(): MediaType? = contentType

    override fun contentLength(): Long = file.length()

    override fun writeTo(sink: BufferedSink) {
        val fileLength = file.length()
        var uploadedBytes = 0L

        file.source().use { source ->
            var read: Long
            val buffer = sink.buffer

            while (source.read(buffer, 8192).also { read = it } != -1L) {
                uploadedBytes += read
                sink.flush()

                // Report progress
                progressCallback?.let { callback ->
                    val progress = ((uploadedBytes.toDouble() / fileLength) * 100).roundToInt()
                    callback(progress)
                }
            }
        }
    }
}