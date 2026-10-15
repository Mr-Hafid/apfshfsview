package com.apfshfsview.core.fs.apfs

import com.apfshfsview.core.binary.LittleEndian

object Fletcher64 {
    private const val MODULUS = 0xFFFFFFFFL

    fun compute(block: ByteArray): Long {
        var sum1 = 0L
        var sum2 = 0L
        for (offset in 8 until block.size step 4) {
            sum1 = (sum1 + LittleEndian.u32(block, offset)) % MODULUS
            sum2 = (sum2 + sum1) % MODULUS
        }
        val check1 = MODULUS - ((sum1 + sum2) % MODULUS)
        val check2 = MODULUS - ((sum1 + check1) % MODULUS)
        return (check2 shl 32) or check1
    }

    fun isValid(block: ByteArray): Boolean = compute(block) == LittleEndian.u64(block, 0)
}
