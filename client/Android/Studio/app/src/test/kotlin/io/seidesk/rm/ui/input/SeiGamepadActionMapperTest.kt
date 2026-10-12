package io.seidesk.rm.ui.input

import io.seidesk.rm.pcstream.XyGamepadPacketV1
import io.seidesk.rm.pcstream.XyGamepadState
import io.seidesk.rm.ui.SeiMouseButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SeiGamepadActionMapperTest {
    private class Recorder : SeiGamepadOutput {
        val pointer = mutableListOf<Pair<Float, Float>>()
        val scroll = mutableListOf<Int>()
        val clicks = mutableListOf<Pair<SeiMouseButton, Boolean>>()
        val keys = mutableListOf<Pair<SeiPadKey, Boolean>>()

        override fun pointerDelta(dx: Float, dy: Float) { pointer += dx to dy }
        override fun scrollUnits(units: Int) { scroll += units }
        override fun mouseClick(button: SeiMouseButton, down: Boolean) { clicks += button to down }
        override fun key(key: SeiPadKey, down: Boolean) { keys += key to down }
    }

    private fun state(buttons: Int = 0, leftX: Int = 0, leftY: Int = 0, rightY: Int = 0) =
        XyGamepadState(buttons = buttons, leftX = leftX, leftY = leftY, rightY = rightY)

    @Test
    fun leftStickMovesPointerAtEighteenUnitsPerDeflection() {
        val out = Recorder()
        SeiGamepadActionMapper.apply(state(leftX = 32_767, leftY = -32_767), null, out)
        assertEquals(1, out.pointer.size)
        assertEquals(18f, out.pointer[0].first, 0.01f)
        assertEquals(-18f, out.pointer[0].second, 0.01f)
        assertTrue(out.scroll.isEmpty() && out.clicks.isEmpty() && out.keys.isEmpty())
    }

    @Test
    fun centeredStickEmitsNothingSoThePointerNeverDrifts() {
        val out = Recorder()
        SeiGamepadActionMapper.apply(state(), null, out)
        assertTrue(out.pointer.isEmpty())
    }

    @Test
    fun buttonPressIsEdgeTriggeredAndNeverSpamsWhileHeld() {
        val out = Recorder()
        val idle = state()
        val pressed = state(buttons = XyGamepadPacketV1.A)

        SeiGamepadActionMapper.apply(pressed, idle, out)
        SeiGamepadActionMapper.apply(pressed, pressed, out)
        SeiGamepadActionMapper.apply(pressed, pressed, out)
        SeiGamepadActionMapper.apply(idle, pressed, out)

        assertEquals(listOf(SeiMouseButton.LEFT to true, SeiMouseButton.LEFT to false), out.clicks)
    }

    @Test
    fun faceButtonsMapToTheSameActionsAsThePhysicalPad() {
        val out = Recorder()
        SeiGamepadActionMapper.apply(state(buttons = XyGamepadPacketV1.B), null, out)
        SeiGamepadActionMapper.apply(state(buttons = XyGamepadPacketV1.X), null, out)
        SeiGamepadActionMapper.apply(state(buttons = XyGamepadPacketV1.Y), null, out)

        assertEquals(SeiMouseButton.RIGHT to true, out.clicks.single())
        assertEquals(SeiPadKey.ENTER to true, out.keys[0])
        assertEquals(SeiPadKey.ESCAPE to true, out.keys[1])
    }

    @Test
    fun dpadEmitsArrowKeysAndShouldersEmitOneScrollNotch() {
        val out = Recorder()
        SeiGamepadActionMapper.apply(state(buttons = XyGamepadPacketV1.DPAD_UP), null, out)
        assertEquals(SeiPadKey.ARROW_UP to true, out.keys.single())

        val scrollOut = Recorder()
        SeiGamepadActionMapper.apply(state(buttons = XyGamepadPacketV1.LEFT_SHOULDER), null, scrollOut)
        assertEquals(listOf(120), scrollOut.scroll)

        val rightOut = Recorder()
        SeiGamepadActionMapper.apply(state(buttons = XyGamepadPacketV1.RIGHT_SHOULDER), null, rightOut)
        assertEquals(listOf(-120), rightOut.scroll)
    }

    @Test
    fun rightStickScrollsProportionallyAndTriggersOnlyOnRisingEdge() {
        val out = Recorder()
        SeiGamepadActionMapper.apply(state(rightY = 32_767), null, out)
        assertEquals(listOf(-48), out.scroll)

        val trigger = Recorder()
        val released = XyGamepadState(rightTrigger = 0)
        val pulled = XyGamepadState(rightTrigger = 200)
        SeiGamepadActionMapper.apply(pulled, released, trigger)
        SeiGamepadActionMapper.apply(pulled, pulled, trigger)
        assertEquals(listOf(-120), trigger.scroll)
    }

    @Test
    fun wasdModeSendsKeyboardKeysInsteadOfMovingThePointer() {
        val out = Recorder()
        val up = state(leftY = -32_767)
        SeiGamepadActionMapper.apply(up, null, out, SeiStickMode.WASD)
        assertEquals(listOf(SeiPadKey.W to true), out.keys)
        assertTrue("stik tidak boleh menggerakkan kursor di mode WASD", out.pointer.isEmpty())

        // Melepas stik harus mengangkat tombol, bukan membiarkannya tersangkut.
        val released = Recorder()
        SeiGamepadActionMapper.apply(state(), up, released, SeiStickMode.WASD)
        assertEquals(listOf(SeiPadKey.W to false), released.keys)
    }

    @Test
    fun diagonalStickPressesTwoKeysAndReversingSwapsThemInOneFrame() {
        val out = Recorder()
        val upRight = state(leftX = 32_767, leftY = -32_767)
        SeiGamepadActionMapper.apply(upRight, null, out, SeiStickMode.WASD)
        assertEquals(setOf(SeiPadKey.W to true, SeiPadKey.D to true), out.keys.toSet())

        val down = Recorder()
        val downLeft = state(leftX = -32_767, leftY = 32_767)
        SeiGamepadActionMapper.apply(downLeft, upRight, down, SeiStickMode.WASD)
        assertEquals(
            setOf(SeiPadKey.W to false, SeiPadKey.D to false, SeiPadKey.S to true, SeiPadKey.A to true),
            down.keys.toSet(),
        )
    }

    @Test
    fun arrowModeUsesArrowKeysAndIgnoresSmallDeflection() {
        val out = Recorder()
        SeiGamepadActionMapper.apply(state(leftX = -32_767), null, out, SeiStickMode.ARROWS)
        assertEquals(listOf(SeiPadKey.ARROW_LEFT to true), out.keys)

        val small = Recorder()
        // 0.3 defleksi di bawah ambang 0.5 -> tidak ada tombol.
        SeiGamepadActionMapper.apply(state(leftX = (0.3f * 32_767).toInt()), null, small, SeiStickMode.ARROWS)
        assertTrue(small.keys.isEmpty())
        assertTrue(small.pointer.isEmpty())
    }

    @Test
    fun pointerModeIsUnchangedByTheNewStickModes() {
        val out = Recorder()
        SeiGamepadActionMapper.apply(state(leftX = 32_767, leftY = -32_767), null, out)
        assertEquals(1, out.pointer.size)
        assertTrue(out.keys.isEmpty())
    }

    @Test
    fun directionsHelperMatchesTheDocumentedThreshold() {
        assertEquals(0, SeiGamepadActionMapper.directions(0f, 0f).size)
        assertEquals(
            setOf(SeiStickDirection.UP, SeiStickDirection.RIGHT),
            SeiGamepadActionMapper.directions(0.9f, -0.9f),
        )
        assertEquals(0, SeiGamepadActionMapper.directions(0.49f, -0.49f).size)
        assertEquals(SeiPadKey.S, SeiGamepadActionMapper.keyFor(SeiStickDirection.DOWN, SeiStickMode.WASD))
        assertEquals(
            SeiPadKey.ARROW_DOWN,
            SeiGamepadActionMapper.keyFor(SeiStickDirection.DOWN, SeiStickMode.ARROWS),
        )
    }

    @Test
    fun everyEmittedValueStaysFiniteAndBounded() {
        val out = Recorder()
        for (axis in intArrayOf(-32_768, -1, 0, 1, 32_767)) {
            SeiGamepadActionMapper.apply(state(leftX = axis, leftY = axis, rightY = axis), null, out)
        }
        out.pointer.forEach { (dx, dy) ->
            assertTrue(abs(dx) <= 18.01f && abs(dy) <= 18.01f)
        }
        out.scroll.forEach { assertTrue(abs(it) <= 120) }
    }
}
