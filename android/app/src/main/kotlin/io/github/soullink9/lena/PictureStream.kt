package io.github.soullink9.lena

import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Reads Home Assistant's camera picture stream (`/api/camera_proxy_stream/<camera>`, MJPEG: one JPEG after
 * another, each with its own short header) one picture at a time. Camera mode's last way to a moving
 * picture ([CameraLive]): Home Assistant fetches every picture from the camera itself, so it works wherever
 * still pictures work, even with the cameras on a network only Home Assistant can reach.
 *
 * Each part's `Content-Length` says how long its picture is (Home Assistant's own stream always sends it).
 * A part without one (a camera's own MJPEG passed through) is read up to the JPEG's end marker instead.
 * Plain Kotlin with no Android in it, so tools/camera_check checks it away from a device.
 */
class PictureStream(private val input: InputStream) {
    companion object {
        /** A picture bigger than this is not a camera picture; the stream is given up on rather than filling memory. */
        const val MAX_PICTURE = 16 * 1024 * 1024
        private const val MAX_HEADER_LINE = 1024
    }

    /** The next picture's JPEG bytes, or null when the stream has ended. */
    fun next(): ByteArray? {
        var length = -1
        var inHeaders = false
        while (true) {
            val line = readLine() ?: return null
            when {
                // The line between parts: its headers follow.
                line.startsWith("--") -> { inHeaders = true; length = -1 }
                inHeaders && line.isEmpty() -> return if (length >= 0) readExactly(length) else readJpeg()
                inHeaders -> {
                    val colon = line.indexOf(':')
                    if (colon > 0 && line.substring(0, colon).trim().equals("Content-Length", ignoreCase = true)) {
                        length = line.substring(colon + 1).trim().toIntOrNull() ?: -1
                        if (length > MAX_PICTURE) throw IllegalStateException("a picture of $length bytes")
                    }
                }
                // Anything before the first part (or the blank line ending the last picture) is skipped.
            }
        }
    }

    /** One line, without its CR LF; null at the end of the stream. */
    private fun readLine(): String? {
        val line = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) return if (line.isEmpty()) null else line.toString()
            if (b == '\n'.code) return line.toString().trimEnd('\r')
            if (line.length < MAX_HEADER_LINE) line.append(b.toChar())
        }
    }

    private fun readExactly(length: Int): ByteArray? {
        val bytes = ByteArray(length)
        var done = 0
        while (done < length) {
            val n = input.read(bytes, done, length - done)
            if (n < 0) return null
            done += n
        }
        return bytes
    }

    /** A picture without a length: everything up to and including the JPEG's end marker (FF D9). */
    private fun readJpeg(): ByteArray? {
        val out = ByteArrayOutputStream()
        var last = -1
        while (true) {
            val b = input.read()
            if (b < 0) return null
            out.write(b)
            if (last == 0xFF && b == 0xD9) return out.toByteArray()
            if (out.size() > MAX_PICTURE) throw IllegalStateException("a picture without an end")
            last = b
        }
    }
}
