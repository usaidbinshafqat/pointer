package app.cursor.android.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant

private val Context.cacheDataStore: DataStore<Preferences> by preferencesDataStore("cursor_cache")

data class CachedConversation(
    val lines: List<ChatLine> = emptyList(),
    val syncedAt: String? = null,
)

class CacheRepository(private val context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val agentsKey = stringPreferencesKey("agents_json")
    private val modelsKey = stringPreferencesKey("models_json")
    private val accountKey = stringPreferencesKey("account_json")
    private val workersKey = stringPreferencesKey("workers_json")
    private val reposKey = stringPreferencesKey("repos_json")

    suspend fun loadAgents(): List<AgentSummary> = decode(agentsKey) ?: emptyList()
    suspend fun saveAgents(items: List<AgentSummary>) = encode(agentsKey, items)

    suspend fun loadModels(): List<CursorModel> = decode(modelsKey) ?: emptyList()
    suspend fun saveModels(items: List<CursorModel>) = encode(modelsKey, items)

    suspend fun loadAccount(): AccountInfo? = decode(accountKey)
    suspend fun saveAccount(account: AccountInfo) = encode(accountKey, account)

    suspend fun loadWorkers(): List<PrivateWorker> = decode(workersKey) ?: emptyList()
    suspend fun saveWorkers(items: List<PrivateWorker>) = encode(workersKey, items)

    suspend fun loadRepositories(): List<GitHubRepository> = decode(reposKey) ?: emptyList()
    suspend fun saveRepositories(items: List<GitHubRepository>) = encode(reposKey, items)

    private val inboxChangeStatsKey = stringPreferencesKey("inbox_change_stats_json")

    suspend fun loadInboxChangeStats(): Map<String, InboxChangeStats> =
        decode(inboxChangeStatsKey) ?: emptyMap()

    suspend fun saveInboxChangeStats(stats: Map<String, InboxChangeStats>) =
        encode(inboxChangeStatsKey, stats)

    suspend fun loadConversation(agentId: String): CachedConversation {
        val lines = decode<List<ChatLine>>(conversationKey(agentId)) ?: emptyList()
        val syncedAt = context.cacheDataStore.data.first()[syncedAtKey(agentId)]
        return CachedConversation(lines = lines, syncedAt = syncedAt)
    }

    suspend fun saveConversation(agentId: String, lines: List<ChatLine>): String {
        val trimmed = lines
            .filterNot { it.queued }
            .map { it.forCache() }
            .takeLast(80)
        val syncedAt = Instant.now().toString()
        encode(conversationKey(agentId), trimmed)
        context.cacheDataStore.edit { prefs ->
            prefs[syncedAtKey(agentId)] = syncedAt
        }
        return syncedAt
    }

    suspend fun deleteConversations(agentIds: Collection<String>) {
        if (agentIds.isEmpty()) return
        context.cacheDataStore.edit { prefs ->
            agentIds.forEach { agentId ->
                prefs.remove(conversationKey(agentId))
                prefs.remove(syncedAtKey(agentId))
            }
        }
    }

    private fun syncedAtKey(agentId: String) =
        stringPreferencesKey(
            "conv_synced_" + agentId.replace(Regex("[^A-Za-z0-9_-]"), "_").take(80),
        )

    private fun conversationKey(agentId: String) =
        stringPreferencesKey(
            "conv_" + agentId.replace(Regex("[^A-Za-z0-9_-]"), "_").take(80),
        )

    private suspend inline fun <reified T> decode(key: Preferences.Key<String>): T? {
        val raw = context.cacheDataStore.data.first()[key].orEmpty()
        if (raw.isBlank()) return null
        return runCatching { json.decodeFromString<T>(raw) }.getOrNull()
    }

    private suspend inline fun <reified T> encode(key: Preferences.Key<String>, value: T) {
        context.cacheDataStore.edit { prefs ->
            prefs[key] = json.encodeToString(value)
        }
    }
}
