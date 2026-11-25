package com.frameforge.app.ui

import android.media.MediaCodec
import android.media.MediaExtractor
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class VideoStudioSampleFlagsTest {
    @Test
    fun mapsSyncSampleToCodecKeyFrameFlag() {
        assertEquals(
            MediaCodec.BUFFER_FLAG_KEY_FRAME,
            mapExtractorSampleFlagsToCodecBufferFlags(MediaExtractor.SAMPLE_FLAG_SYNC)
        )
    }

    @Test
    fun mapsPartialSampleFlagWithoutPassingExtractorBitsThrough() {
        assertEquals(
            MediaCodec.BUFFER_FLAG_PARTIAL_FRAME,
            mapExtractorSampleFlagsToCodecBufferFlags(MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME)
        )
    }

    @Test
    fun combinesSupportedSampleFlags() {
        assertEquals(
            MediaCodec.BUFFER_FLAG_KEY_FRAME or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME,
            mapExtractorSampleFlagsToCodecBufferFlags(
                MediaExtractor.SAMPLE_FLAG_SYNC or MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME
            )
        )
    }

    @Test
    fun rejectsEncryptedSamplesThatCannotBeSafelyRemuxed() {
        assertThrows(IOException::class.java) {
            mapExtractorSampleFlagsToCodecBufferFlags(MediaExtractor.SAMPLE_FLAG_ENCRYPTED)
        }
    }
}
