package io.seidesk.rm.ui

/**
 * Peta kode error koneksi XyDesk dan pesan native FreeRDP menjadi penjelasan
 * yang bisa ditindaklanjuti. Pesan mentah tetap tersedia sebagai detail
 * teknis; penyebab spesifik hanya ditampilkan bila pesannya memang menyatakan itu.
 */
internal object RdpErrors {

    /** Returns actionable hint, or null if no specific explanation is available. */
    fun hint(code: String, message: String): String? {
        val m = message.lowercase()
        return when {
            // Probe TCP gagal: host/port tidak menjawab
            code == "unreachable" ->
                "The host did not answer on the RDP port. Check the address and port, " +
                    "host availability, enabled RDP, and the firewall/network."

            // Watchdog: proses koneksi tidak selesai tepat waktu
            code == "connect_timeout" ->
                "The connection attempt timed out. Check the network, host, and " +
                    "TLS/NLA settings; Details shows the native message when available."

            code == "connect_failed" && (m.contains("wrong password") || m.contains("password supplied")) ->
                "FreeRDP reported a wrong password. Check the remote account password."

            code == "connect_failed" && m.contains("account locked") ->
                "The server reported that the account is locked. Unlock it on the host or contact its administrator."

            code == "connect_failed" && m.contains("password") &&
                (m.contains("expired") || m.contains("must be changed")) ->
                "The server reported an expired password. Change it using a method supported by the host."

            code == "connect_failed" && (m.contains("certificate") || m.contains("tls") || m.contains("ssl")) ->
                "TLS/certificate negotiation failed. Check the device clock, host certificate, and TLS support."

            code == "connect_failed" && (m.contains("dns") || m.contains("could not be resolved") ||
                m.contains("host name was not found")) ->
                "FreeRDP could not resolve the host name. Check DNS or use the correct host address."

            code == "connect_failed" && (m.contains("logon failed") || m.contains("logon failure") ||
                m.contains("authentication")) ->
                "The server rejected logon but did not identify which field was wrong. Check the username, domain, " +
                    "account status, and host logon policy."

            code == "connect_failed" && m.contains("server denied") ->
                "The server explicitly denied the connection. Check the host's RDP policy and session limits."

            code == "connect_failed" ->
                "FreeRDP did not complete the handshake. The exact cause was not provided; check Details, " +
                    "the network, host, and RDP configuration."

            code == "connect_exception" ->
                "The RDP engine failed to start the connection (internal error). Open Details for the technical log."

            else -> null
        }
    }

    /** Hint untuk disconnect hanya bila native detail menyebut alasan tertentu. */
    fun disconnectedHint(message: String?): String? {
        val m = message?.lowercase().orEmpty()
        return when {
            m.contains("another user connected") || m.contains("forcing the disconnection") ->
                "The server reported that another connection took over this session."

            m.contains("active session limit timer") || m.contains("logon timeout") ->
                "The server reported that the active-session time limit was reached."

            m.contains("idle session limit timer") ->
                "The server reported that the idle-session time limit was reached."

            m.contains("server denied connection") ->
                "The server reported that the connection was denied."

            else -> null
        }
    }
}
