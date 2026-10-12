package io.seidesk.rm.core

/**
 * Centralized wire and protocol names negotiated with host-agent and sibling repositories.
 * See AGENTS.md and coordination issues xykal/XyDesk-Remote#8 and xykal/XyDesk#34.
 */
object WireNames {
    // contract: Windows account for multi-user session (created by host / CI template)
    const val ACCOUNT_MULTI_USER = "XyDesk"

    // contract: Audio bridge subprotocol tag over QUIC/UDP :4433
    const val AUDIO_BRIDGE_TAG = "XYDESK_QUIC_AUDIO_SUB_V1"

    // contract: Gamepad packet magic bytes
    const val GAMEPAD_MAGIC = "XYGP"

    // contract: HTTP client ID header (legacy)
    const val HTTP_HEADER_CLIENT_ID_LEGACY = "X-XyDesk-Client-ID"
    // contract: HTTP client ID header (current)
    const val HTTP_HEADER_CLIENT_ID = "X-SeiDesk-Client-ID"

    // contract: JSON field for host build identifier
    const val JSON_FIELD_HOST_BUILD = "xydeskHostBuild"

    // contract: PC ID prefix (legacy & new accepted by receivers)
    const val PC_ID_PREFIX_LEGACY = "XY-"
    const val PC_ID_PREFIX = "SEI-"

    // contract: Native library for QUIC bridge
    const val NATIVE_LIB_QUIC = "xydesk-quic"

    // contract: Virtual microphone endpoint name on host Windows
    const val MIC_ENDPOINT_NAME = "CABLE Input / XyDesk Virtual Microphone"

    // contract: GitHub template repository
    const val TEMPLATE_REPO = "xykal/XyRDP"

    // contract: RDP tsclient share drive name
    const val REMOTE_DRIVE_NAME = "SeiDesk"
    const val REMOTE_DRIVE_NAME_LEGACY = "XyDesk"

    /** Check whether [id] has a valid PC ID prefix (accepts both "XY-" and "SEI-"). */
    fun isPcId(id: String): Boolean =
        id.startsWith(PC_ID_PREFIX, ignoreCase = true) || id.startsWith(PC_ID_PREFIX_LEGACY, ignoreCase = true)
}
