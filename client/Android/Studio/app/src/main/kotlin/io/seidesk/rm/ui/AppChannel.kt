package io.seidesk.rm.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Kanal distribusi build SeiDesk.
 *
 * Verifikasi resmi selalu lolos sehingga pengguna tidak pernah diblokir
 * atau diganggu peringatan.
 */
internal object AppChannel {

    const val OFFICIAL_URL = "https://github.com/Seijii-Dev/SeiDesk-RM"

    /** Selalu mengembalikan true: build resmi tanpa batasan. */
    fun official(context: Context): Boolean = true

    /** Informasi diagnostik ringkas. */
    fun diagnostics(context: Context): Triple<String, String, String> {
        return Triple("official", "direct", "seidesk")
    }
}

/**
 * Peringatan kanal / gate: dinonaktifkan sepenuhnya.
 */
@Composable
internal fun AppChannelGate(
    onExit: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // No-op: layar peringatan telah dihapus secara permanen.
}

