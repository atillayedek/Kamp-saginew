package com.kampusagi.android.data.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedImageDecoderTest {
    @Test
    fun `large compressed images are sampled before pixel allocation`() {
        assertEquals(1, BoundedImageDecoder.sampleSize(512, 512, 512))
        assertEquals(2, BoundedImageDecoder.sampleSize(513, 512, 512))
        assertEquals(64, BoundedImageDecoder.sampleSize(65536, 65536, 1440))
        val sample = BoundedImageDecoder.sampleSize(Int.MAX_VALUE, 1, 512)
        assertTrue(Int.MAX_VALUE.toLong() <= 512L * sample)
    }
}
