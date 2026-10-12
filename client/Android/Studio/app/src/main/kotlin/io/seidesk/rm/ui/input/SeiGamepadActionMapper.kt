package io.seidesk.rm.ui.input

import io.seidesk.rm.pcstream.XyGamepadPacketV1
import io.seidesk.rm.pcstream.XyGamepadState
import io.seidesk.rm.ui.SeiMouseButton
import kotlin.math.abs

/** Keys the virtual pad can emit; the session translates these to Android key codes. */
enum class SeiPadKey {
    ENTER,
    ESCAPE,
    ARROW_UP,
    ARROW_DOWN,
    ARROW_LEFT,
    ARROW_RIGHT,
    W,
    A,
    S,
    D,
}

/**
 * Tujuan stik kiri. [POINTER] menggerakkan kursor (perilaku lama), [WASD] dan
 * [ARROWS] mengirim tombol papan ketik sehingga stik bisa dipakai untuk game
 * yang tidak menerima gamepad.
 */
enum class SeiStickMode {
    POINTER,
    WASD,
    ARROWS,
}

/** Arah digital yang diturunkan dari simpangan stik analog. */
enum class SeiStickDirection {
    UP,
    DOWN,
    LEFT,
    RIGHT,
}

/**
 * Output side of a gamepad. Implemented by the RDP session with the same hooks the
 * physical Bluetooth/USB pad already uses (pointer delta, scroll units, mouse click,
 * virtual key), so both pads end up on one code path.
 */
interface SeiGamepadOutput {
    fun pointerDelta(dx: Float, dy: Float)
    fun scrollUnits(units: Int)
    fun mouseClick(button: SeiMouseButton, down: Boolean)
    fun key(key: SeiPadKey, down: Boolean)
}

/**
 * Translates gamepad state into RDP-side actions.
 *
 * Semantics intentionally mirror the existing physical-pad mapping in
 * `SeiDeskSessionActivity` so an on-screen pad and a Bluetooth pad behave the same:
 * A = left click, B = right click, X = Enter, Y = Escape, left stick moves the
 * pointer at 18 units per full deflection, shoulders scroll one notch, and the
 * right stick / triggers scroll proportionally.
 *
 * Pure Kotlin (no Android imports) so the mapping is unit-testable on the JVM.
 */
object SeiGamepadActionMapper {
    /** Pointer units per full stick deflection; matches the physical pad. */
    const val POINTER_UNITS_PER_DEFLECTION = 18f

    /** One scroll notch, in wheel units; matches the physical shoulder buttons. */
    const val SHOULDER_SCROLL_UNITS = 120

    /** Scroll units at full right-stick / trigger deflection. */
    const val SCROLL_UNITS_PER_DEFLECTION = 48f

    /** Trigger fraction that counts as "pressed" for scroll notches. */
    const val TRIGGER_THRESHOLD = 0.5f

    private const val AXIS_DENOMINATOR = 32_767f
    private const val STICK_EPSILON = 1e-3f

    /** Simpangan stik minimum agar arah digital (WASD/panah) aktif. */
    const val STICK_KEY_THRESHOLD = 0.5f

    private val EMPTY = XyGamepadState()

    /**
     * Emit the actions implied by moving from [previous] to [state].
     *
     * Sticks are continuous (like a physical `ACTION_MOVE` stream); buttons and
     * triggers are edge-triggered so holding a control never spams the host.
     */
    fun apply(
        state: XyGamepadState,
        previous: XyGamepadState?,
        out: SeiGamepadOutput,
        stickMode: SeiStickMode = SeiStickMode.POINTER,
    ) {
        val before = previous ?: EMPTY

        val leftX = axis(state.leftX)
        val leftY = axis(state.leftY)
        if (stickMode == SeiStickMode.POINTER) {
            if (abs(leftX) > STICK_EPSILON || abs(leftY) > STICK_EPSILON) {
                out.pointerDelta(
                    leftX * POINTER_UNITS_PER_DEFLECTION,
                    leftY * POINTER_UNITS_PER_DEFLECTION,
                )
            }
        } else {
            // Mode papan ketik: arah diturunkan dari stik, dikirim sebagai
            // edge (turun/naik) supaya menahan stik tidak mengulang tombol.
            val now = directions(leftX, leftY)
            val was = directions(axis(before.leftX), axis(before.leftY))
            for (direction in SeiStickDirection.entries) {
                val isDown = direction in now
                if (isDown != (direction in was)) {
                    out.key(keyFor(direction, stickMode), isDown)
                }
            }
        }

        val rightY = axis(state.rightY)
        if (abs(rightY) > STICK_EPSILON) {
            val units = (-rightY * SCROLL_UNITS_PER_DEFLECTION).toInt()
            if (units != 0) out.scrollUnits(units)
        }

        if (crossed(before.leftTrigger, state.leftTrigger)) out.scrollUnits(SHOULDER_SCROLL_UNITS)
        if (crossed(before.rightTrigger, state.rightTrigger)) out.scrollUnits(-SHOULDER_SCROLL_UNITS)

        edge(before.buttons, state.buttons, XyGamepadPacketV1.A) { down ->
            out.mouseClick(SeiMouseButton.LEFT, down)
        }
        edge(before.buttons, state.buttons, XyGamepadPacketV1.B) { down ->
            out.mouseClick(SeiMouseButton.RIGHT, down)
        }
        edge(before.buttons, state.buttons, XyGamepadPacketV1.LEFT_THUMB) { down ->
            out.mouseClick(SeiMouseButton.MIDDLE, down)
        }
        edge(before.buttons, state.buttons, XyGamepadPacketV1.X) { down ->
            out.key(SeiPadKey.ENTER, down)
        }
        edge(before.buttons, state.buttons, XyGamepadPacketV1.Y) { down ->
            out.key(SeiPadKey.ESCAPE, down)
        }
        edge(before.buttons, state.buttons, XyGamepadPacketV1.DPAD_UP) { down ->
            out.key(SeiPadKey.ARROW_UP, down)
        }
        edge(before.buttons, state.buttons, XyGamepadPacketV1.DPAD_DOWN) { down ->
            out.key(SeiPadKey.ARROW_DOWN, down)
        }
        edge(before.buttons, state.buttons, XyGamepadPacketV1.DPAD_LEFT) { down ->
            out.key(SeiPadKey.ARROW_LEFT, down)
        }
        edge(before.buttons, state.buttons, XyGamepadPacketV1.DPAD_RIGHT) { down ->
            out.key(SeiPadKey.ARROW_RIGHT, down)
        }
        // START/BACK have no unambiguous RDP target and are intentionally unmapped;
        // they still travel to a PC/Game Stream host inside the packet.
        edge(before.buttons, state.buttons, XyGamepadPacketV1.LEFT_SHOULDER) { down ->
            if (down) out.scrollUnits(SHOULDER_SCROLL_UNITS)
        }
        edge(before.buttons, state.buttons, XyGamepadPacketV1.RIGHT_SHOULDER) { down ->
            if (down) out.scrollUnits(-SHOULDER_SCROLL_UNITS)
        }
    }

    /**
     * Arah digital dari stik analog. Ambang [STICK_KEY_THRESHOLD] dipakai agar
     * simpangan kecil tidak memicu tombol; diagonal menghasilkan dua arah.
     */
    fun directions(x: Float, y: Float): Set<SeiStickDirection> {
        val out = mutableSetOf<SeiStickDirection>()
        if (y <= -STICK_KEY_THRESHOLD) out += SeiStickDirection.UP
        if (y >= STICK_KEY_THRESHOLD) out += SeiStickDirection.DOWN
        if (x <= -STICK_KEY_THRESHOLD) out += SeiStickDirection.LEFT
        if (x >= STICK_KEY_THRESHOLD) out += SeiStickDirection.RIGHT
        return out
    }

    /** Kunci untuk satu arah pada mode stik papan ketik. */
    fun keyFor(direction: SeiStickDirection, mode: SeiStickMode): SeiPadKey = when (direction) {
        SeiStickDirection.UP -> if (mode == SeiStickMode.WASD) SeiPadKey.W else SeiPadKey.ARROW_UP
        SeiStickDirection.DOWN -> if (mode == SeiStickMode.WASD) SeiPadKey.S else SeiPadKey.ARROW_DOWN
        SeiStickDirection.LEFT -> if (mode == SeiStickMode.WASD) SeiPadKey.A else SeiPadKey.ARROW_LEFT
        SeiStickDirection.RIGHT -> if (mode == SeiStickMode.WASD) SeiPadKey.D else SeiPadKey.ARROW_RIGHT
    }

    private fun axis(rawValue: Int): Float = rawValue / AXIS_DENOMINATOR

    private inline fun edge(before: Int, after: Int, bit: Int, emit: (Boolean) -> Unit) {
        val was = (before and bit) != 0
        val now = (after and bit) != 0
        if (was != now) emit(now)
    }

    /** True only on the rising edge past [TRIGGER_THRESHOLD]. */
    private fun crossed(before: Int, after: Int): Boolean =
        before < TRIGGER_THRESHOLD * 255f && after >= TRIGGER_THRESHOLD * 255f
}
