package id.vern.antigravity.service

import android.content.Context
import id.vern.antigravity.executor.EngineExecutor

object ConversationTitleGenerator {

    private const val MAX_TITLE_CHARS = 48
    private const val FALLBACK_MAX_CHARS = 38

    fun buildImmediateFallbackTitle(userPrompt: String, attachmentName: String? = null): String {
        val rawLine = userPrompt
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            ?: attachmentName?.trim()
            ?: return "New Conversation"

        val cleaned = rawLine
            .replace(Regex("https?://\\S+"), "")
            .replace(Regex("[`*_#>\\[\\]]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { rawLine }

        return truncateAtWordBoundary(cleaned, FALLBACK_MAX_CHARS)
            .ifBlank { "New Conversation" }
    }

    fun pickLightweightModelId(
        availableModels: List<CliModel>,
        fallbackModelId: String?
    ): String? {
        val cleanedFallback = fallbackModelId?.let { AgyModelsService.cleanModelId(it) }?.takeIf { it.isNotBlank() }
        if (availableModels.isEmpty()) return cleanedFallback

        if (cleanedFallback != null) {
            val matchingBase = availableModels.firstOrNull { it.cliId.equals(cleanedFallback, ignoreCase = true) }?.displayName
            if (matchingBase != null) {
                val lowVariant = availableModels.firstOrNull {
                    it.displayName.equals(matchingBase, ignoreCase = true) && it.effort.equals("Low", ignoreCase = true)
                }
                if (lowVariant != null) return AgyModelsService.cleanModelId(lowVariant.cliId)
            }
        }

        val anyLowVariant = availableModels.firstOrNull { it.effort.equals("Low", ignoreCase = true) }
        if (anyLowVariant != null) return AgyModelsService.cleanModelId(anyLowVariant.cliId)

        return cleanedFallback ?: AgyModelsService.cleanModelId(availableModels.first().cliId)
    }

    suspend fun generateContextualTitle(
        context: Context,
        userPrompt: String,
        assistantResponse: String,
        availableModels: List<CliModel>,
        fallbackModelId: String?
    ): String? {
        val userSnippet = userPrompt.trim().take(420)
        val assistantSnippet = assistantResponse.trim().take(500)
        if (userSnippet.isBlank() && assistantSnippet.isBlank()) return null

        val modelId = pickLightweightModelId(availableModels, fallbackModelId)

        val titlePrompt = buildString {
            appendLine("Create a short, natural conversation title (3 to 6 words) that captures the main topic.")
            appendLine("Use the same language as the user message (e.g. Indonesian if Indonesian, English if English).")
            appendLine("Reply with ONLY the title. No quotes, no markdown, no prefix, no trailing period.")
            appendLine()
            appendLine("User: $userSnippet")
            if (assistantSnippet.isNotBlank()) {
                appendLine("Assistant: $assistantSnippet")
            }
        }

        val rawOutput = EngineExecutor.runAgyQuickPrint(
            context = context,
            prompt = titlePrompt,
            cliModelId = modelId,
            timeoutMs = 15_000L
        ) ?: return null

        return sanitizeGeneratedTitle(rawOutput)
    }

    fun sanitizeGeneratedTitle(rawOutput: String): String? {
        val candidateLine = rawOutput
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { line ->
                line.isNotBlank() &&
                    !line.startsWith("!") &&
                    !line.startsWith("Fetching ") &&
                    !line.startsWith("[") &&
                    !line.lowercase().startsWith("error:") &&
                    !line.lowercase().startsWith("warning:")
            } ?: return null

        var cleaned = candidateLine
            .replace(Regex("^(title|judul)\\s*[:\\-]\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("^[#*\\-•>\\s]+"), "")
            .trim()
            .trim('"', '\'', '`', '*', '_', '.')
            .replace(Regex("\\s+"), " ")
            .trim()

        if (cleaned.length < 3) return null

        val lower = cleaned.lowercase()
        if (lower.startsWith("error") ||
            lower.contains("timed out") ||
            lower.contains("unauthorized") ||
            lower.contains("failed to ") ||
            lower == "new conversation"
        ) {
            return null
        }

        if (cleaned.length > MAX_TITLE_CHARS) {
            cleaned = truncateAtWordBoundary(cleaned, MAX_TITLE_CHARS)
        }

        return cleaned.takeIf { it.length >= 3 }
    }

    private fun truncateAtWordBoundary(text: String, maxChars: Int): String {
        if (text.length <= maxChars) return text
        val slice = text.take(maxChars)
        val lastSpace = slice.lastIndexOf(' ')
        val cut = if (lastSpace >= maxChars / 2) slice.substring(0, lastSpace) else slice
        return cut.trimEnd(',', '.', ';', ':', '-', ' ') + "…"
    }
}
