package io.github.soullink9.lena

/**
 * A QR code for a short piece of text, such as the address a TV shows for "Connect from your phone"
 * ([HaPairActivity]): your phone's camera opens it, so nothing has to be typed.
 *
 * Written here rather than added as a library: Lena only needs short addresses, so this covers QR versions
 * 1 to 6 (up to 106 characters) at error correction level M, which every phone camera reads. It follows the
 * QR code standard (ISO/IEC 18004) step by step: the text as bytes, Reed-Solomon error correction, the
 * fixed patterns, the data in its zig-zag, and the mask that's easiest to read. Plain Kotlin, so it can be
 * checked away from Android.
 */
object Qr {
    /** Error correction level M's format bits (00) and, per version 1 to 6, its codewords per block and blocks. */
    private const val LEVEL_M_BITS = 0
    private val ECC_PER_BLOCK = intArrayOf(10, 16, 26, 18, 24, 16)
    private val BLOCKS = intArrayOf(1, 1, 1, 2, 2, 4)

    /**
     * The QR code for [text]: [size] rows of [size] modules, true where dark (row, then column). Without
     * the quiet border round it, which whoever draws it adds. Throws if the text is too long.
     */
    fun encode(text: String): Array<BooleanArray> {
        val data = text.toByteArray(Charsets.UTF_8)
        val version = (1..6).firstOrNull { dataCodewords(it) * 8 >= 4 + 8 + data.size * 8 }
            ?: throw IllegalArgumentException("Too long for a QR code here: ${data.size} bytes")
        return Symbol(version).apply { draw(codewords(version, data)) }.modules
    }

    /** Total codewords in a version: its modules, less the fixed patterns, in bytes. */
    private fun rawCodewords(version: Int): Int {
        var bits = (16 * version + 128) * version + 64
        if (version >= 2) bits -= 25 // one alignment pattern (versions 2 to 6), less where it crosses nothing
        return bits / 8
    }

    private fun dataCodewords(version: Int) = rawCodewords(version) - ECC_PER_BLOCK[version - 1] * BLOCKS[version - 1]

    /**
     * The bytes as a QR symbol stores them: byte mode, the length, the text, then padding to fill the
     * version; split into blocks, each with its error correction, interleaved.
     */
    private fun codewords(version: Int, text: ByteArray): IntArray {
        val capacity = dataCodewords(version)
        val bits = ArrayList<Boolean>()
        fun put(value: Int, count: Int) {
            for (i in count - 1 downTo 0) bits.add((value ushr i) and 1 == 1)
        }
        put(0b0100, 4) // byte mode
        put(text.size, 8) // its length (8 bits up to version 9)
        for (b in text) put(b.toInt() and 0xFF, 8)
        put(0, minOf(4, capacity * 8 - bits.size)) // the terminator
        while (bits.size % 8 != 0) bits.add(false)
        var pad = 0
        while (bits.size < capacity * 8) put(if (pad++ % 2 == 0) 0xEC else 0x11, 8)
        val data = IntArray(capacity) { i -> (0 until 8).fold(0) { acc, j -> (acc shl 1) or (if (bits[i * 8 + j]) 1 else 0) } }

        // Blocks: the short ones first, the rest one data codeword longer (only from version 5 on in other
        // levels; at level M up to version 6 they're all the same length, but the rule costs nothing).
        val blocks = BLOCKS[version - 1]
        val eccLen = ECC_PER_BLOCK[version - 1]
        val raw = rawCodewords(version)
        val shortBlocks = blocks - raw % blocks
        val shortLen = raw / blocks
        val divisor = rsDivisor(eccLen)
        val dataBlocks = ArrayList<IntArray>()
        val eccBlocks = ArrayList<IntArray>()
        var at = 0
        for (i in 0 until blocks) {
            val len = shortLen - eccLen + if (i < shortBlocks) 0 else 1
            val block = data.copyOfRange(at, at + len)
            at += len
            dataBlocks.add(block)
            eccBlocks.add(rsRemainder(block, divisor))
        }
        // Interleave: the first codeword of each block, then the second, and so on; then the same for the
        // error correction.
        val out = ArrayList<Int>(raw)
        for (i in 0 until dataBlocks.maxOf { it.size }) for (b in dataBlocks) if (i < b.size) out.add(b[i])
        for (i in 0 until eccLen) for (b in eccBlocks) out.add(b[i])
        return out.toIntArray()
    }

    /** Multiplication in GF(256) with the QR polynomial x⁸ + x⁴ + x³ + x² + 1. */
    private fun gfMultiply(x: Int, y: Int): Int {
        var z = 0
        for (i in 7 downTo 0) {
            z = (z shl 1) xor ((z ushr 7) * 0x11D)
            z = z xor (((y ushr i) and 1) * x)
        }
        return z
    }

    /** Reed-Solomon's generator polynomial of this degree (highest term left out, as it's always 1). */
    private fun rsDivisor(degree: Int): IntArray {
        val result = IntArray(degree)
        result[degree - 1] = 1
        var root = 1
        repeat(degree) {
            for (j in 0 until degree) {
                result[j] = gfMultiply(result[j], root)
                if (j + 1 < degree) result[j] = result[j] xor result[j + 1]
            }
            root = gfMultiply(root, 0x02)
        }
        return result
    }

    /** The error correction codewords for [data]: the remainder of dividing it by [divisor]. */
    private fun rsRemainder(data: IntArray, divisor: IntArray): IntArray {
        val result = IntArray(divisor.size)
        for (b in data) {
            val factor = b xor result[0]
            System.arraycopy(result, 1, result, 0, result.size - 1)
            result[result.size - 1] = 0
            for (i in result.indices) result[i] = result[i] xor gfMultiply(divisor[i], factor)
        }
        return result
    }

    /** One QR symbol being drawn: its modules, and which of them are fixed patterns rather than data. */
    private class Symbol(val version: Int) {
        val size = version * 4 + 17
        val modules = Array(size) { BooleanArray(size) }
        private val fixed = Array(size) { BooleanArray(size) }

        private fun set(x: Int, y: Int, dark: Boolean) {
            modules[y][x] = dark
            fixed[y][x] = true
        }

        /** The fixed patterns, the data, then the mask that reads best with its format bits. */
        fun draw(codewords: IntArray) {
            for (i in 0 until size) { // timing lines
                set(6, i, i % 2 == 0)
                set(i, 6, i % 2 == 0)
            }
            finder(3, 3)
            finder(size - 4, 3)
            finder(3, size - 4)
            if (version >= 2) alignment(size - 7, size - 7) // versions 2 to 6 have one, bottom right
            format(0) // reserves the format areas; written for real once the mask is chosen
            place(codewords)

            var best = 0
            var bestScore = Int.MAX_VALUE
            for (mask in 0 until 8) {
                flip(mask)
                format(mask)
                val score = penalty()
                if (score < bestScore) {
                    best = mask
                    bestScore = score
                }
                flip(mask) // masking twice undoes it
            }
            flip(best)
            format(best)
        }

        /** A finder pattern (the three big squares) with its light border, centred on (x, y). */
        private fun finder(x: Int, y: Int) {
            for (dy in -4..4) for (dx in -4..4) {
                val distance = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
                val xx = x + dx
                val yy = y + dy
                if (xx in 0 until size && yy in 0 until size) set(xx, yy, distance != 2 && distance != 4)
            }
        }

        /** An alignment pattern (the small square), centred on (x, y). */
        private fun alignment(x: Int, y: Int) {
            for (dy in -2..2) for (dx in -2..2) set(x + dx, y + dy, maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy)) != 1)
        }

        /** The format bits (level M and the mask, with their own error correction), in both places. */
        private fun format(mask: Int) {
            val value = (LEVEL_M_BITS shl 3) or mask
            var rem = value
            repeat(10) { rem = (rem shl 1) xor ((rem ushr 9) * 0x537) }
            val bits = ((value shl 10) or rem) xor 0x5412
            fun bit(i: Int) = (bits ushr i) and 1 == 1
            for (i in 0..5) set(8, i, bit(i))
            set(8, 7, bit(6))
            set(8, 8, bit(7))
            set(7, 8, bit(8))
            for (i in 9 until 15) set(14 - i, 8, bit(i))
            for (i in 0 until 8) set(size - 1 - i, 8, bit(i))
            for (i in 8 until 15) set(8, size - 15 + i, bit(i))
            set(8, size - 8, true) // the dark module, always dark
        }

        /** The codewords' bits in the zig-zag: two columns at a time, from the bottom right, up then down. */
        private fun place(codewords: IntArray) {
            var i = 0
            var right = size - 1
            while (right >= 1) {
                if (right == 6) right = 5 // the vertical timing line isn't part of any pair
                for (vert in 0 until size) for (j in 0..1) {
                    val x = right - j
                    val upward = (right + 1) and 2 == 0
                    val y = if (upward) size - 1 - vert else vert
                    if (!fixed[y][x] && i < codewords.size * 8) {
                        modules[y][x] = (codewords[i ushr 3] ushr (7 - (i and 7))) and 1 == 1
                        i++
                    }
                }
                right -= 2
            }
        }

        /** Inverts the data modules where this mask pattern says so. */
        private fun flip(mask: Int) {
            for (y in 0 until size) for (x in 0 until size) {
                if (fixed[y][x]) continue
                val invert = when (mask) {
                    0 -> (x + y) % 2 == 0
                    1 -> y % 2 == 0
                    2 -> x % 3 == 0
                    3 -> (x + y) % 3 == 0
                    4 -> (x / 3 + y / 2) % 2 == 0
                    5 -> x * y % 2 + x * y % 3 == 0
                    6 -> (x * y % 2 + x * y % 3) % 2 == 0
                    else -> ((x + y) % 2 + x * y % 3) % 2 == 0
                }
                if (invert) modules[y][x] = !modules[y][x]
            }
        }

        /**
         * How hard the symbol is to read, by the standard's four rules: long runs of one colour, 2×2 blocks,
         * things that look like a finder pattern, and too much of one colour. Lower is better.
         */
        private fun penalty(): Int {
            var score = 0
            fun line(get: (Int) -> Boolean) {
                var run = 1
                for (i in 1 until size) {
                    if (get(i) == get(i - 1)) run++ else {
                        if (run >= 5) score += 3 + run - 5
                        run = 1
                    }
                }
                if (run >= 5) score += 3 + run - 5
                // dark-light-dark×3-light-dark with four light modules on either side
                val pattern = booleanArrayOf(true, false, true, true, true, false, true)
                for (i in 0..size - 7) {
                    if ((0 until 7).all { get(i + it) == pattern[it] }) {
                        val before = (1..4).all { i - it < 0 || !get(i - it) }
                        val after = (1..4).all { i + 6 + it >= size || !get(i + 6 + it) }
                        if (before || after) score += 40
                    }
                }
            }
            for (y in 0 until size) line { modules[y][it] }
            for (x in 0 until size) line { modules[it][x] }
            for (y in 0 until size - 1) for (x in 0 until size - 1) {
                val c = modules[y][x]
                if (c == modules[y][x + 1] && c == modules[y + 1][x] && c == modules[y + 1][x + 1]) score += 3
            }
            val dark = modules.sumOf { row -> row.count { it } }
            val percent = dark * 100 / (size * size)
            score += kotlin.math.abs(percent - 50) / 5 * 10
            return score
        }
    }
}
