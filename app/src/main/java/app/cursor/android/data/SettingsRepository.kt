package app.cursor.android.data

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore("cursor_settings")

private const val SECRETS_PREFS = "pointer_secrets"
private const val LEGACY_FALLBACK_PREFS = "${SECRETS_PREFS}_local"
private const val API_KEY_PREF = "api_key"

val FontWeightIds = listOf(
    "thin",
    "extralight",
    "light",
    "regular",
    "medium",
    "semibold",
    "bold",
    "extrabold",
    "black",
)

private val allowedFontWeights = FontWeightIds.toSet()

data class CursorSettings(
    val apiKey: String = "",
    val secureStorageAvailable: Boolean = true,
    val machineName: String = "",
    val defaultRepoUrl: String = "",
    val envType: String = "machine",
    val modelId: String = AUTO_MODEL_ID,
    val themeMode: String = "system",
    val dynamicColor: Boolean = true,
    val spinnerStyle: String = "wavy",
    val textScale: String = "m",
    val fontWeight: String = "regular",
    val haptics: Boolean = true,
    val showDiff: Boolean = true,
    val showRuntime: Boolean = true,
    val showUpdated: Boolean = true,
)

class SettingsRepository(private val context: Context) {
    private val apiKeyKey = stringPreferencesKey("api_key")
    private val apiKeyGenKey = intPreferencesKey("api_key_gen")
    private val machineNameKey = stringPreferencesKey("machine_name")
    private val defaultRepoUrlKey = stringPreferencesKey("default_repo_url")
    private val envTypeKey = stringPreferencesKey("env_type")
    private val modelIdKey = stringPreferencesKey("model_id")
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val dynamicColorKey = booleanPreferencesKey("dynamic_color")
    private val spinnerStyleKey = stringPreferencesKey("spinner_style")
    private val textScaleKey = stringPreferencesKey("text_scale")
    private val fontWeightKey = stringPreferencesKey("font_weight")
    private val hapticsKey = booleanPreferencesKey("haptics")
    private val showDiffKey = booleanPreferencesKey("show_diff")
    private val showRuntimeKey = booleanPreferencesKey("show_runtime")
    private val showUpdatedKey = booleanPreferencesKey("show_updated")

    private val secretsResult: Result<SharedPreferences> by lazy {
        runCatching { createSecretsPrefs() }
    }

    val settings: Flow<CursorSettings> = context.settingsDataStore.data.map { prefs ->
        val secureStorageAvailable = secretsResult.isSuccess
        val apiKey = readApiKeyOrNull().orEmpty()
        val storedModel = prefs[modelIdKey]
        CursorSettings(
            apiKey = apiKey,
            secureStorageAvailable = secureStorageAvailable,
            machineName = prefs[machineNameKey].orEmpty(),
            defaultRepoUrl = prefs[defaultRepoUrlKey].orEmpty(),
            envType = prefs[envTypeKey] ?: "machine",
            modelId = when {
                storedModel == null -> AUTO_MODEL_ID
                storedModel == "composer-2.5" -> AUTO_MODEL_ID
                storedModel.equals("default", ignoreCase = true) -> AUTO_MODEL_ID
                storedModel.isBlank() -> AUTO_MODEL_ID
                else -> storedModel
            },
            themeMode = prefs[themeModeKey]
                ?.takeIf { it in setOf("system", "light", "dark") }
                ?: "system",
            dynamicColor = prefs[dynamicColorKey] ?: true,
            spinnerStyle = prefs[spinnerStyleKey]
                ?.takeIf { it in setOf("wavy", "circular", "linearWavy", "linear") }
                ?: "wavy",
            textScale = prefs[textScaleKey]
                ?.takeIf { it in setOf("s", "m", "l", "xl") }
                ?: "m",
            fontWeight = prefs[fontWeightKey]
                ?.takeIf { it in allowedFontWeights }
                ?: "regular",
            haptics = prefs[hapticsKey] ?: true,
            showDiff = prefs[showDiffKey] ?: true,
            showRuntime = prefs[showRuntimeKey] ?: true,
            showUpdated = prefs[showUpdatedKey] ?: true,
        )
    }

    suspend fun save(settings: CursorSettings) {
        context.settingsDataStore.edit { prefs ->
            prefs.remove(apiKeyKey)
            prefs[machineNameKey] = settings.machineName.trim()
            prefs[defaultRepoUrlKey] = settings.defaultRepoUrl.trim()
            prefs[envTypeKey] = settings.envType.trim().ifEmpty { "machine" }
            prefs[modelIdKey] = settings.modelId.trim().ifEmpty { AUTO_MODEL_ID }
            prefs[themeModeKey] = settings.themeMode
                .takeIf { it in setOf("system", "light", "dark") }
                ?: "system"
            prefs[dynamicColorKey] = settings.dynamicColor
            prefs[spinnerStyleKey] = settings.spinnerStyle
                .takeIf { it in setOf("wavy", "circular", "linearWavy", "linear") }
                ?: "wavy"
            prefs[textScaleKey] = settings.textScale
                .takeIf { it in setOf("s", "m", "l", "xl") }
                ?: "m"
            prefs[fontWeightKey] = settings.fontWeight
                .takeIf { it in allowedFontWeights }
                ?: "regular"
            prefs[hapticsKey] = settings.haptics
            prefs[showDiffKey] = settings.showDiff
            prefs[showRuntimeKey] = settings.showRuntime
            prefs[showUpdatedKey] = settings.showUpdated
        }
    }

    /** API keys may only be persisted after the caller has verified them with Cursor. */
    suspend fun saveVerifiedApiKey(settings: CursorSettings) {
        val key = settings.apiKey.trim()
        writeApiKey(key)
        context.settingsDataStore.edit { prefs ->
            prefs.remove(apiKeyKey)
            prefs[apiKeyGenKey] = (prefs[apiKeyGenKey] ?: 0) + 1
        }
        save(settings)
    }

    /** Move a plaintext DataStore key into encrypted prefs, then delete the plaintext copy. */
    suspend fun migrateLegacyApiKey() {
        val fallback = context.getSharedPreferences(LEGACY_FALLBACK_PREFS, Context.MODE_PRIVATE)
        val fallbackKey = fallback.getString(API_KEY_PREF, "").orEmpty()
        var shouldDeleteFallbackKey = false
        context.settingsDataStore.edit { prefs ->
            val legacyKey = prefs[apiKeyKey].orEmpty()
            val existingEncryptedKey = if (secretsResult.isSuccess) {
                runCatching { readApiKeyOrNull() }.getOrNull()
            } else {
                null
            }
            val plaintextSecured = canDeleteLegacyApiKeyMaterial(
                legacyKey = legacyKey,
                fallbackKey = fallbackKey,
                existingEncryptedKey = existingEncryptedKey,
                secureStorageAvailable = secretsResult.isSuccess,
                writeAndConfirm = { candidate ->
                    runCatching {
                        writeApiKey(candidate)
                        readApiKeyOrNull() == candidate
                    }.getOrDefault(false)
                },
            )
            if (plaintextSecured) {
                shouldDeleteFallbackKey = fallbackKey.isNotBlank()
                prefs.remove(apiKeyKey)
                prefs[apiKeyGenKey] = (prefs[apiKeyGenKey] ?: 0) + 1
            }
        }
        if (shouldDeleteFallbackKey) {
            fallback.edit().remove(API_KEY_PREF).commit()
        }
    }

    /** Regular is the UI default. Older builds stored light implicitly. */
    suspend fun migrateDefaultFontWeight() {
        context.settingsDataStore.edit { prefs ->
            val current = prefs[fontWeightKey]
            if (current.isNullOrBlank() || current == "light") {
                prefs[fontWeightKey] = "regular"
            }
        }
    }

    private fun readApiKeyOrNull(): String? =
        secretsResult.getOrNull()?.getString(API_KEY_PREF, "")

    private fun writeApiKey(value: String) {
        val secrets = secretsResult.getOrElse {
            throw SecureStorageUnavailableException(it)
        }
        if (!secrets.edit().putString(API_KEY_PREF, value).commit()) {
            throw SecureStorageUnavailableException()
        }
    }

    private fun createSecretsPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            SECRETS_PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
}

class SecureStorageUnavailableException(cause: Throwable? = null) :
    IllegalStateException(
        "secure storage isn't available on this device. restart pointer and try again",
        cause,
    )

/**
 * Returns true only when plaintext key material can safely be removed.
 *
 * An existing encrypted key is authoritative. Otherwise, the selected legacy key must be
 * successfully written and read back by [writeAndConfirm] before either plaintext source is
 * eligible for deletion.
 */
internal fun canDeleteLegacyApiKeyMaterial(
    legacyKey: String,
    fallbackKey: String,
    existingEncryptedKey: String?,
    secureStorageAvailable: Boolean,
    writeAndConfirm: (String) -> Boolean,
): Boolean {
    val candidate = legacyKey.ifBlank { fallbackKey }
    if (candidate.isBlank()) return false
    if (!existingEncryptedKey.isNullOrBlank()) return true
    if (!secureStorageAvailable) return false
    return runCatching { writeAndConfirm(candidate) }.getOrDefault(false)
}
