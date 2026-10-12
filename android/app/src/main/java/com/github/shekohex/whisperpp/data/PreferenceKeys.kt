package com.github.shekohex.whisperpp.data

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

typealias PreferenceKey<T> = Preferences.Key<T>

/**
 * Single source of truth for every DataStore key. Key names are persisted on disk and in
 * backups: never rename them, add a migration instead.
 */
object PreferenceKeys {
    // Settings DataStore ("settings")
    val SPEECH_TO_TEXT_BACKEND = stringPreferencesKey("speech-to-text-backend")
    val ACTIVE_STT_PROVIDER_ID = stringPreferencesKey("active-stt-provider-id")
    val ACTIVE_STT_MODEL_ID = stringPreferencesKey("active-stt-model-id")
    val ACTIVE_TEXT_PROVIDER_ID = stringPreferencesKey("active-text-provider-id")
    val ACTIVE_TEXT_MODEL_ID = stringPreferencesKey("active-text-model-id")
    val COMMAND_TEXT_PROVIDER_ID = stringPreferencesKey("command-text-provider-id")
    val COMMAND_TEXT_MODEL_ID = stringPreferencesKey("command-text-model-id")
    val ENDPOINT = stringPreferencesKey("endpoint")
    val LANGUAGE_CODE = stringPreferencesKey("language-code")
    val API_KEY = stringPreferencesKey("api-key")
    val MODEL = stringPreferencesKey("model")
    val AUTO_RECORDING_START = booleanPreferencesKey("is-auto-recording-start")
    val AUTO_SWITCH_BACK = booleanPreferencesKey("auto-switch-back")
    val AUTO_TRANSCRIBE_ON_PAUSE = booleanPreferencesKey("auto-transcribe-on-pause")
    val CANCEL_CONFIRMATION = booleanPreferencesKey("cancel-confirmation")
    val ADD_TRAILING_SPACE = booleanPreferencesKey("add-trailing-space")
    val POSTPROCESSING = stringPreferencesKey("postprocessing")
    val TIMEOUT = intPreferencesKey("timeout")
    val PROMPT = stringPreferencesKey("prompt")
    val USE_CONTEXT = booleanPreferencesKey("use-context")

    /** Stream audio to realtime-capable STT models (§4.5.1); batch remains the fallback. Default true. */
    val REALTIME_STT_ENABLED = booleanPreferencesKey("realtime-stt-enabled")

    val ENHANCEMENT_PRESET_ID = stringPreferencesKey("enhancement-preset-id")
    val COMMAND_PRESET_ID = stringPreferencesKey("command-preset-id")

    val ENHANCEMENT_ENABLED = booleanPreferencesKey("smart-fix-enabled")
    val ENHANCEMENT_BACKEND = stringPreferencesKey("smart-fix-backend")
    val ENHANCEMENT_ENDPOINT = stringPreferencesKey("smart-fix-endpoint")
    val ENHANCEMENT_API_KEY = stringPreferencesKey("smart-fix-api-key")
    val ENHANCEMENT_MODEL = stringPreferencesKey("smart-fix-model")
    val ENHANCEMENT_TEMPERATURE = floatPreferencesKey("smart-fix-temperature")
    val ENHANCEMENT_PROMPT = stringPreferencesKey("smart-fix-prompt")

    val HAPTIC_FEEDBACK_ENABLED = booleanPreferencesKey("haptic-feedback-enabled")
    val SOUND_EFFECTS_ENABLED = booleanPreferencesKey("sound-effects-enabled")
    val PAUSE_MEDIA_WHILE_RECORDING = booleanPreferencesKey("pause-media-while-recording")
    val TAP_TO_RECORD = booleanPreferencesKey("tap-to-record")
    val SECURE_FIELD_EXPLANATION_DONT_SHOW_AGAIN = booleanPreferencesKey("secure-field-explanation-dont-show-again")
    val VERBOSE_NETWORK_LOGS_ENABLED = booleanPreferencesKey("verbose-network-logs-enabled")
    val DISCLOSURE_SHOWN_DICTATION_AUDIO = booleanPreferencesKey("disclosure-shown-dictation-audio")
    val DISCLOSURE_SHOWN_ENHANCEMENT_TEXT = booleanPreferencesKey("disclosure-shown-enhancement-text")
    val DISCLOSURE_SHOWN_COMMAND_TEXT = booleanPreferencesKey("disclosure-shown-command-text")
    val PER_APP_SEND_POLICY_JSON = stringPreferencesKey("per-app-send-policy-json")
    val MIC_PERMISSION_REQUESTED = booleanPreferencesKey("mic-permission-requested")

    val UPDATE_CHANNEL = stringPreferencesKey("update-channel")

    val PROVIDERS_JSON = stringPreferencesKey("providers_json")
    val PROFILES_JSON = stringPreferencesKey("profiles_json")
    val GLOBAL_BASE_PROMPT = stringPreferencesKey("global_base_prompt")
    val PROMPT_PROFILES_JSON = stringPreferencesKey("prompt_profiles_json")
    val APP_PROMPT_MAPPINGS_JSON = stringPreferencesKey("app_prompt_mappings_json")
    val PROVIDER_API_KEY_MIGRATION_DONE = booleanPreferencesKey("provider_api_key_migration_done")
    val PROVIDER_SCHEMA_V2_MIGRATION_DONE = booleanPreferencesKey("provider_schema_v2_migration_done")
    val PROVIDER_SCHEMA_V3_MIGRATION_DONE = booleanPreferencesKey("provider_schema_v3_migration_done")
    val PROVIDER_SELECTIONS_V2_MIGRATION_DONE = booleanPreferencesKey("provider_selections_v2_migration_done")
    val PROMPTS_PROFILES_SCHEMA_V1_MIGRATION_DONE = booleanPreferencesKey("prompts_profiles_schema_v1_migration_done")

    val SETTINGS: List<PreferenceKey<*>> = listOf(
        SPEECH_TO_TEXT_BACKEND,
        ACTIVE_STT_PROVIDER_ID,
        ACTIVE_STT_MODEL_ID,
        ACTIVE_TEXT_PROVIDER_ID,
        ACTIVE_TEXT_MODEL_ID,
        COMMAND_TEXT_PROVIDER_ID,
        COMMAND_TEXT_MODEL_ID,
        ENDPOINT,
        LANGUAGE_CODE,
        API_KEY,
        MODEL,
        AUTO_RECORDING_START,
        AUTO_SWITCH_BACK,
        AUTO_TRANSCRIBE_ON_PAUSE,
        CANCEL_CONFIRMATION,
        ADD_TRAILING_SPACE,
        POSTPROCESSING,
        TIMEOUT,
        PROMPT,
        USE_CONTEXT,
        ENHANCEMENT_PRESET_ID,
        COMMAND_PRESET_ID,
        ENHANCEMENT_ENABLED,
        REALTIME_STT_ENABLED,
        ENHANCEMENT_BACKEND,
        ENHANCEMENT_ENDPOINT,
        ENHANCEMENT_API_KEY,
        ENHANCEMENT_MODEL,
        ENHANCEMENT_TEMPERATURE,
        ENHANCEMENT_PROMPT,
        HAPTIC_FEEDBACK_ENABLED,
        SOUND_EFFECTS_ENABLED,
        PAUSE_MEDIA_WHILE_RECORDING,
        TAP_TO_RECORD,
        SECURE_FIELD_EXPLANATION_DONT_SHOW_AGAIN,
        VERBOSE_NETWORK_LOGS_ENABLED,
        DISCLOSURE_SHOWN_DICTATION_AUDIO,
        DISCLOSURE_SHOWN_ENHANCEMENT_TEXT,
        DISCLOSURE_SHOWN_COMMAND_TEXT,
        PER_APP_SEND_POLICY_JSON,
        MIC_PERMISSION_REQUESTED,
        UPDATE_CHANNEL,
        PROVIDERS_JSON,
        PROFILES_JSON,
        GLOBAL_BASE_PROMPT,
        PROMPT_PROFILES_JSON,
        APP_PROMPT_MAPPINGS_JSON,
        PROVIDER_API_KEY_MIGRATION_DONE,
        PROVIDER_SCHEMA_V2_MIGRATION_DONE,
        PROVIDER_SCHEMA_V3_MIGRATION_DONE,
        PROVIDER_SELECTIONS_V2_MIGRATION_DONE,
        PROMPTS_PROFILES_SCHEMA_V1_MIGRATION_DONE,
    )

    // Analytics DataStore ("analytics")
    object Analytics {
        val TOTAL_COMPLETED_SESSIONS = intPreferencesKey("analytics_total_completed_sessions")
        val TOTAL_CANCELLED_SESSIONS = intPreferencesKey("analytics_total_cancelled_sessions")
        val TOTAL_RECORDING_DURATION_MILLIS = longPreferencesKey("analytics_total_recording_duration_millis")
        val TOTAL_RAW_WORD_COUNT = intPreferencesKey("analytics_total_raw_word_count")
        val TOTAL_FINAL_INSERTED_WORD_COUNT = intPreferencesKey("analytics_total_final_inserted_word_count")
        val TOTAL_INSERTED_CHARACTER_COUNT = intPreferencesKey("analytics_total_inserted_character_count")
        val DAILY_BUCKETS_JSON = stringPreferencesKey("analytics_daily_buckets_json")

        val ALL: List<PreferenceKey<*>> = listOf(
            TOTAL_COMPLETED_SESSIONS,
            TOTAL_CANCELLED_SESSIONS,
            TOTAL_RECORDING_DURATION_MILLIS,
            TOTAL_RAW_WORD_COUNT,
            TOTAL_FINAL_INSERTED_WORD_COUNT,
            TOTAL_INSERTED_CHARACTER_COUNT,
            DAILY_BUCKETS_JSON,
        )
    }

    // Updater DataStore ("updater_state")
    object Updater {
        val DOWNLOAD_ID = longPreferencesKey("download_id")
        val EXPECTED_SIGNATURE = stringPreferencesKey("expected_signature")
        val TARGET_VERSION = stringPreferencesKey("target_version")
        val DESTINATION_PATH = stringPreferencesKey("destination_path")

        val ALL: List<PreferenceKey<*>> = listOf(
            DOWNLOAD_ID,
            EXPECTED_SIGNATURE,
            TARGET_VERSION,
            DESTINATION_PATH,
        )
    }
}
