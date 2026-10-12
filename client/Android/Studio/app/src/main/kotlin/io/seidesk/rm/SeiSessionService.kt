package io.seidesk.rm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import io.seidesk.rm.ui.SpotifyMediaBridge
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import io.seidesk.rm.ui.AppPrefs
import androidx.core.content.ContextCompat
import io.seidesk.rm.ui.xyNow
import io.seidesk.rm.ui.SeiDeskHomeActivity
import io.seidesk.rm.ui.SeiSessionRegistry

/**
 * Aksi notifikasi diarahkan ke registry sesi dalam proses ini.
 */
/**
 * Foreground service sesi SeiDesk RM.
 *
 * Kenapa ada: Android mematikan proses di latar kalau tidak ada service yang
 * berjalan di depan. Sesi RDP adalah pekerjaan yang sedang dilihat user, jadi
 * statusnya dinaikkan ke foreground + notifikasi yang jelas (bukan tersembunyi
 * — user harus bisa memutuskan sesi dari notifikasi).
 *
 * Tipe `specialUse`: sesuai definisi Android, untuk kasus yang tidak masuk
 * kategori bawaan (sesi remote desktop interaktif yang jalan terus di latar).
 * Aplikasi ini di-sideload, jadi tidak ada ketergantungan kebijakan store.
 */
class SeiSessionService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    /** Kapan sesi dimulai; dipakai kronometer di notifikasi. */
    private var startedAt = 0L

    override fun onCreate() {
        super.onCreate()
        startedAt = System.currentTimeMillis()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                if (SeiSessionRegistry.count() == 0) stopSelf()
                else SeiSessionRegistry.disconnectAll()
                return START_NOT_STICKY
            }

            ACTION_PING -> {
                // Sudah jalan: cukup perbarui teksnya.
                startForeground(NOTIF_ID, buildNotification(intent.getStringExtra(EXTRA_LABEL)))
                return START_NOT_STICKY
            }

            // Tombol notifikasi tambahan (permintaan pemilik 2026-10-10):
            // Beranda tanpa memutus sesi + jeda/putar musik dari notifikasi.
            ACTION_HOME -> {
                runCatching {
                    startActivity(
                        Intent(this, SeiDeskHomeActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
                return START_NOT_STICKY
            }

            ACTION_MUSIC -> {
                runCatching { SpotifyMediaBridge.playPause() }
                return START_NOT_STICKY
            }

            else -> {
                startForeground(NOTIF_ID, buildNotification(intent?.getStringExtra(EXTRA_LABEL)))
                return START_NOT_STICKY
            }
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            xyNow("Sesi aktif", "Active session"),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = xyNow(
                "Notifikasi selama sesi remote berjalan di latar",
                "Notification while a remote session runs in the background",
            )
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(label: String?): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, SeiDeskHomeActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, SeiSessionService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val home = PendingIntent.getService(
            this,
            2,
            Intent(this, SeiSessionService::class.java).setAction(ACTION_HOME),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val music = PendingIntent.getService(
            this,
            3,
            Intent(this, SeiSessionService::class.java).setAction(ACTION_MUSIC),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val host = label ?: "SeiDesk RM"
        val prefs = AppPrefs(this)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            // Ikon app sendiri (mark monokrom) — dulu pakai
            // android.R.drawable.stat_sys_upload_done, ikon sistem panah
            // upload yang tidak ada hubungannya dengan remote desktop.
            .setSmallIcon(io.seidesk.rm.R.drawable.ic_launcher_monochrome)
            .setContentTitle(xyNow("Sesi remote aktif", "Remote session active"))
            .setContentText(host)
            .setSubText(versionLabel())
            .setContentIntent(open)
            .addAction(0, xyNow("Buka", "Open"), open)
        if (prefs.notifHomeButton) builder.addAction(0, xyNow("Beranda", "Home"), home)
        if (prefs.notifMusicButton) builder.addAction(0, xyNow("Musik", "Music"), music)
        builder
            .addAction(0, xyNow("Putuskan", "Disconnect"), stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setStyle(NotificationCompat.BigTextStyle().bigText(host))

        // Tampilan penuh warna: hanya berlaku untuk foreground service, dan
        // service ini memang foreground. Tanpa ini notifikasi memakai kartu
        // putih/abu bawaan Android yang tidak mirip app sama sekali.
        builder.setColor(NOTIF_ACCENT)
            .setColorized(true)

        // Kronometer = lama sesi berjalan, tanpa perlu memperbarui notifikasi.
        if (startedAt > 0L) {
            builder.setWhen(startedAt).setUsesChronometer(true)
        }

        // Ikon besar di sisi kiri. Dipakai PNG (bukan vektor) karena
        // BitmapFactory tidak membaca drawable vektor.
        runCatching {
            BitmapFactory.decodeResource(resources, io.seidesk.rm.R.drawable.xy_mark_white)
        }.getOrNull()?.let { builder.setLargeIcon(it) }

        // Layar kunci tidak menampilkan host yang sedang disambungkan.
        builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(io.seidesk.rm.R.drawable.ic_launcher_monochrome)
                    .setContentTitle(xyNow("Sesi remote aktif", "Remote session active"))
                    .setContentText("SeiDesk RM")
                    .setColor(NOTIF_ACCENT)
                    .setColorized(true)
                    .build(),
            )

        return builder.build()
    }

    /** `v1.1.1 (63)` untuk subteks notifikasi; fallback kalau tidak terbaca. */
    private fun versionLabel(): String = runCatching {
        val info = packageManager.getPackageInfo(packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        "v${info.versionName} ($code)"
    }.getOrDefault("SeiDesk RM")

    companion object {
        /** Warna aksen monokrom app, dipakai notifikasi berwarna. */
        private const val NOTIF_ACCENT = 0xFF111114.toInt()

        private const val CHANNEL_ID = "seidesk_sesi_aktif"
        private const val NOTIF_ID = 4711
        const val ACTION_STOP = "io.seidesk.rm.action.STOP_SESSION"
        const val ACTION_PING = "io.seidesk.rm.action.PING_SESSION"
        const val ACTION_HOME = "io.seidesk.rm.action.GO_HOME"
        const val ACTION_MUSIC = "io.seidesk.rm.action.MUSIC_TOGGLE"
        private const val EXTRA_LABEL = "label"

        /** Mulai/perbarui service. Aman dipanggil berulang. */
        fun start(context: Context, label: String?, ping: Boolean = false) {
            val intent = Intent(context, SeiSessionService::class.java).apply {
                putExtra(EXTRA_LABEL, label)
                if (ping) action = ACTION_PING
            }
            runCatching {
                if (Build.VERSION.SDK_INT >= 26) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, SeiSessionService::class.java))
            }
        }
    }
}
