package com.openear.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.openear.app.transcription.JniWhisper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on a device/emulator: checks the JNI library loads and rejects bad input safely. */
@RunWith(AndroidJUnit4::class)
class NativeBridgeTest {
    @Test fun libraryLoads() = assertTrue(JniWhisper.isAvailable)

    @Test fun invalidModelPathReturnsNullHandle() =
        assertEquals(0L, JniWhisper.init("/nonexistent/model.bin"))

    @Test fun zeroHandleOperationsAreSafeNoOps() {
        JniWhisper.free(0L)
        JniWhisper.free(0L) // repeated close
        JniWhisper.abort(0L)
        assertNull(JniWhisper.transcribe(0L, FloatArray(16_000), 2, "auto", 0))
        assertNull(JniWhisper.timings(0L))
    }
}
