package xyz.mycompany.friendalert.utils

/**
 * Centralized place for all system configuration keys stored in the database.
 * This ensures consistency and prevents magic strings across the application.
 */
object GlobalConfigKeys {
    // --- Database Keys (Used in settings table) ---
    const val GLOBAL_FREQ_FREQUENT = "global_freq_frequent"
    const val GLOBAL_FREQ_OCCASIONAL = "global_freq_occasional"
    const val GLOBAL_FREQ_RARE = "global_freq_rare"

    // Helper map to translate the display mode name (e.g., "FREQUENT") to the database key constant
    fun mapModeToKey(modeName: String): String {
        return when (modeName) {
            "FREQUENT" -> GLOBAL_FREQ_FREQUENT
            "OCCASIONAL" -> GLOBAL_FREQ_OCCASIONAL
            "RARE" -> GLOBAL_FREQ_RARE
            else -> throw IllegalArgumentException("Unknown frequency mode: $modeName")
        }
    }

    /** Retrieves the global default days for a specific mode name. */
    fun getGlobalDays(modeName: String): Int? {
        return when (modeName) {
            "FREQUENT" -> GLOBAL_FREQ_FREQUENT?.let {
                // This requires calling DAO/Repo, so this helper is only for conceptual use in the UI.
                null
            }
            "OCCASIONAL" -> GLOBAL_FREQ_OCCASIONAL?.let { null }
            "RARE" -> GLOBAL_FREQ_RARE?.let { null }
            else -> null
        }
    }
}
