package id.vern.antigravity.data

import id.vern.antigravity.service.CliModel

data class QuotaBucket(
    val bucketId: String,
    val displayName: String,
    val window: String = "",
    val resetTime: String? = null,
    val description: String = "",
    val remainingFraction: Double = 0.0,
    val disabled: Boolean = false,
    val subtext: String? = null
) {
    val percentage: Int get() = kotlin.math.floor(remainingFraction * 100.0).toInt().coerceIn(0, 100)
}

enum class ModelAvailability {
    AVAILABLE,
    UNAVAILABLE,
    UNKNOWN
}

data class AvailableModelInfo(
    val modelId: String,
    val displayName: String,
    val provider: String,
    val remainingFraction: Double? = null,
    val resetTime: String? = null,
    val availability: ModelAvailability = ModelAvailability.UNKNOWN,
    val groupName: String? = null
) {
    val percentage: Int? get() = remainingFraction?.let { kotlin.math.floor(it * 100.0).toInt().coerceIn(0, 100) }

    fun matches(query: String): Boolean {
        if (query.isBlank()) return false
        if (modelId.equals(query, ignoreCase = true) || displayName.equals(query, ignoreCase = true)) return true

        val qNorm = normalizeForMatch(query)
        val idNorm = normalizeForMatch(modelId)
        val dispNorm = normalizeForMatch(displayName)

        if (qNorm.isNotBlank()) {
            if (qNorm == idNorm || qNorm == dispNorm) return true
            val qTokens = qNorm.split(" ").filter { it.isNotBlank() }
            val idTokens = idNorm.split(" ").filter { it.isNotBlank() }
            val dispTokens = dispNorm.split(" ").filter { it.isNotBlank() }
            if (qTokens == idTokens || qTokens == dispTokens) return true
        }
        return false
    }

    companion object {
        private val PARENTHESIS_REGEX = Regex("""\s*\([^)]*\)""")
        private val TIERED_REGEX = Regex("""[-_]tiered(?=[-_]|$)""", RegexOption.IGNORE_CASE)
        private val EFFORT_REGEX = Regex("""[-_](?:thinking|high|medium|low|xhigh|max)(?=[-_]|$)""", RegexOption.IGNORE_CASE)
        private val NON_ALPHANUM_REGEX = Regex("""[^a-zA-Z0-9.]""")

        fun normalizeForMatch(s: String): String {
            var res = s.replace(PARENTHESIS_REGEX, "")
            res = res.replace(TIERED_REGEX, "")
            res = res.replace(EFFORT_REGEX, "")
            res = res.replace(NON_ALPHANUM_REGEX, " ").lowercase()
            return res.split(Regex("""\s+""")).filter { it.isNotBlank() }.joinToString(" ")
        }
    }
}

enum class SubscriptionTierType {
    FREE,
    PRO,
    ULTRA,
    ENTERPRISE
}

data class QuotaGroup(
    val displayName: String,
    val description: String? = null,
    val sharedModels: List<String> = emptyList(),
    val buckets: List<QuotaBucket> = emptyList(),
    val modelIds: Set<String> = emptySet()
)

data class UserQuotaSummary(
    val groups: List<QuotaGroup> = emptyList(),
    val availableModels: List<AvailableModelInfo> = emptyList(),
    val description: String? = null,
    val modelsFetchFailed: Boolean = false,
    val pickerModels: List<CliModel> = emptyList()
)

data class TierInfo(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val upgradeSubscriptionUri: String = "",
    val upgradeSubscriptionText: String = ""
)

data class CodeAssistTier(
    val currentTier: TierInfo = TierInfo(),
    val paidTier: TierInfo? = null
) {
    val tierType: SubscriptionTierType get() {
        val paidId = paidTier?.id?.lowercase() ?: ""
        val paidName = paidTier?.name?.lowercase() ?: ""
        val curId = currentTier.id.lowercase()
        val curName = currentTier.name.lowercase()

        return when {
            paidId.contains("ultra") || paidName.contains("ultra") || paidId.contains("helium") || curId.contains("ultra") || curId.contains("helium") -> SubscriptionTierType.ULTRA
            paidId.contains("pro") || paidName.contains("pro") || paidId.contains("premium") || curId.contains("pro") || curId.contains("premium") -> SubscriptionTierType.PRO
            paidId.contains("enterprise") || paidId.startsWith("gcp_") || curId.contains("enterprise") || curId.startsWith("gcp_") || curId.contains("standard") -> SubscriptionTierType.ENTERPRISE
            else -> SubscriptionTierType.FREE
        }
    }

    val normalizedTierString: String get() = when (tierType) {
        SubscriptionTierType.ULTRA -> "ULTRA"
        SubscriptionTierType.PRO -> "PRO"
        SubscriptionTierType.ENTERPRISE -> "ENTERPRISE"
        SubscriptionTierType.FREE -> "FREE"
    }
}

