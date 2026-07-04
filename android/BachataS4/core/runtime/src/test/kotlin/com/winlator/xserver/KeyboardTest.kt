package com.winlator.xserver

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class KeyboardTest {
    @Test
    fun createsX11QueryKeymapBitmap() {
        val expected = ByteArray(32).apply {
            this[1] = 0x01
            this[31] = 0x80.toByte()
        }

        assertArrayEquals(
            expected,
            Keyboard.createKeymap(listOf(8.toByte(), 255.toByte())),
        )
    }
}
