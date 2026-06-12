package com.lexipopup.domain.models

// ── Layer ID constants ────────────────────────────────────────────────────────
const val LAYER_CACHE      = "cache"
const val LAYER_HISTORY    = "word_history"
const val LAYER_OFFLINE_DB = "offline_db"
const val LAYER_ONLINE_API = "online_api"
const val LAYER_GROQ_AI    = "groq_ai"
const val LAYER_OPENAI     = "openai"
const val LAYER_ON_DEVICE  = "on_device_ai"
const val LAYER_RULE_BASED = "rule_based"

val ALL_LAYER_IDS = listOf(
    LAYER_CACHE, LAYER_HISTORY, LAYER_OFFLINE_DB, LAYER_ONLINE_API,
    LAYER_GROQ_AI, LAYER_OPENAI, LAYER_ON_DEVICE, LAYER_RULE_BASED
)

val DEFAULT_LAYER_ORDER = listOf(
    LAYER_CACHE, LAYER_HISTORY, LAYER_OFFLINE_DB, LAYER_ONLINE_API,
    LAYER_GROQ_AI, LAYER_OPENAI, LAYER_ON_DEVICE, LAYER_RULE_BASED
)

val DEFAULT_LAYER_ENABLED: Map<String, Boolean> = mapOf(
    LAYER_CACHE      to true,
    LAYER_HISTORY    to true,
    LAYER_OFFLINE_DB to true,
    LAYER_ONLINE_API to true,
    LAYER_GROQ_AI    to true,
    LAYER_OPENAI     to false,
    LAYER_ON_DEVICE  to false,
    LAYER_RULE_BASED to true
)

// ── Per-layer configuration models ───────────────────────────────────────────

data class CacheLayerConfig(
    val maxSize: Int    = 200,
    val ttlMode: String = "session"  // "session" | "1h" | "1d" | "forever"
)

data class OfflineDbLayerConfig(
    val fuzzyMatchEnabled: Boolean  = true,
    val fuzzyMatchThreshold: Int    = 80,
    val includeEtymology: Boolean   = true,
    val maxExamples: Int            = 2
)

data class OnlineApiLayerConfig(
    val timeoutSeconds: Int = 2,
    val retryCount: Int     = 2,
    val cacheResults: Boolean = true
)

data class GroqAiLayerConfig(
    val model: String               = "llama-3.3-70b-versatile",
    val maxTokens: Int              = 150,
    val explanationSentences: Int   = 3,
    val includeExample: Boolean     = true,
    val timeoutSeconds: Int         = 3
)

data class OpenAiLayerConfig(
    val model: String       = "gpt-4o-mini",
    val maxTokens: Int      = 150,
    val timeoutSeconds: Int = 5
)

data class OnDeviceLayerConfig(
    val modelType: String = "gemma-2b-tiny"
)

data class WordHistoryLayerConfig(
    val minAccessCount: Int     = 1,       // word must have been looked up ≥ N times
    val includeAiSourced: Boolean = true   // also include words fetched via online/AI layers
)

data class RuleBasedLayerConfig(
    val mode: String          = "enhanced",  // "basic" | "enhanced" | "off"
    val showAiBadge: Boolean  = true
)

data class GlobalLayerConfig(
    val maxTotalLookupMs: Int       = 5000,
    val parallelLookup: Boolean     = false,
    val showLoadingSkeleton: Boolean = true
)

// ── Full system config bundle ─────────────────────────────────────────────────

data class LayerSystemConfig(
    val layerOrder: List<String>            = DEFAULT_LAYER_ORDER,
    val layerEnabled: Map<String, Boolean>  = DEFAULT_LAYER_ENABLED,
    val cacheConfig: CacheLayerConfig       = CacheLayerConfig(),
    val historyConfig: WordHistoryLayerConfig = WordHistoryLayerConfig(),
    val offlineDbConfig: OfflineDbLayerConfig = OfflineDbLayerConfig(),
    val onlineApiConfig: OnlineApiLayerConfig = OnlineApiLayerConfig(),
    val groqAiConfig: GroqAiLayerConfig     = GroqAiLayerConfig(),
    val openAiConfig: OpenAiLayerConfig     = OpenAiLayerConfig(),
    val onDeviceConfig: OnDeviceLayerConfig = OnDeviceLayerConfig(),
    val ruleBasedConfig: RuleBasedLayerConfig = RuleBasedLayerConfig(),
    val globalConfig: GlobalLayerConfig     = GlobalLayerConfig()
) {
    fun activeLayers(): List<String> =
        layerOrder.filter { layerEnabled[it] == true }
}

// ── Quick presets ─────────────────────────────────────────────────────────────

enum class LookupPreset(
    val displayName: String,
    val emoji: String,
    val description: String,
    val order: List<String>,
    val enabled: Map<String, Boolean>
) {
    SPEED_DEMON(
        "Speed Demon", "🏃", "Fastest possible — cache + offline only",
        DEFAULT_LAYER_ORDER,
        mapOf(
            LAYER_CACHE to true, LAYER_HISTORY to true, LAYER_OFFLINE_DB to true,
            LAYER_ONLINE_API to false, LAYER_GROQ_AI to false,
            LAYER_OPENAI to false, LAYER_ON_DEVICE to false, LAYER_RULE_BASED to false
        )
    ),
    STUDENT(
        "Student", "🎓", "Best definitions + AI explanations",
        DEFAULT_LAYER_ORDER,
        mapOf(
            LAYER_CACHE to true, LAYER_HISTORY to true, LAYER_OFFLINE_DB to true,
            LAYER_ONLINE_API to true, LAYER_GROQ_AI to true,
            LAYER_OPENAI to false, LAYER_ON_DEVICE to false, LAYER_RULE_BASED to true
        )
    ),
    OFFLINE_WARRIOR(
        "Offline", "📡", "Works without internet",
        listOf(LAYER_CACHE, LAYER_HISTORY, LAYER_OFFLINE_DB, LAYER_ON_DEVICE, LAYER_RULE_BASED,
               LAYER_ONLINE_API, LAYER_GROQ_AI, LAYER_OPENAI),
        mapOf(
            LAYER_CACHE to true, LAYER_HISTORY to true, LAYER_OFFLINE_DB to true,
            LAYER_ONLINE_API to false, LAYER_GROQ_AI to false,
            LAYER_OPENAI to false, LAYER_ON_DEVICE to true, LAYER_RULE_BASED to true
        )
    ),
    ZERO_COST(
        "Zero Cost", "💰", "No API costs — no AI keys needed",
        listOf(LAYER_CACHE, LAYER_HISTORY, LAYER_OFFLINE_DB, LAYER_ONLINE_API, LAYER_RULE_BASED,
               LAYER_GROQ_AI, LAYER_OPENAI, LAYER_ON_DEVICE),
        mapOf(
            LAYER_CACHE to true, LAYER_HISTORY to true, LAYER_OFFLINE_DB to true,
            LAYER_ONLINE_API to true, LAYER_GROQ_AI to false,
            LAYER_OPENAI to false, LAYER_ON_DEVICE to false, LAYER_RULE_BASED to true
        )
    ),
    MAX_QUALITY(
        "Max Quality", "🧠", "Best explanations — all AI layers first",
        listOf(LAYER_CACHE, LAYER_HISTORY, LAYER_GROQ_AI, LAYER_OPENAI, LAYER_ON_DEVICE,
               LAYER_OFFLINE_DB, LAYER_ONLINE_API, LAYER_RULE_BASED),
        mapOf(
            LAYER_CACHE to true, LAYER_HISTORY to true, LAYER_OFFLINE_DB to true,
            LAYER_ONLINE_API to true, LAYER_GROQ_AI to true,
            LAYER_OPENAI to true, LAYER_ON_DEVICE to true, LAYER_RULE_BASED to true
        )
    )
}
