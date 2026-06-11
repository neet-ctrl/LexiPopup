package com.lexipopup.data.download

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.downloadDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "lexi_download_state"
)

data class PackInstallState(
    val pack: DatabasePack,
    val isInstalled: Boolean,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val installedWordCount: Int
)

@Singleton
class DownloadStateStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val store: DataStore<Preferences> = context.downloadDataStore

    private fun installedKey(pack: DatabasePack) = booleanPreferencesKey("${pack.name}_installed")
    private fun downloadedBytesKey(pack: DatabasePack) = longPreferencesKey("${pack.name}_downloaded_bytes")
    private fun totalBytesKey(pack: DatabasePack) = longPreferencesKey("${pack.name}_total_bytes")
    private fun wordCountKey(pack: DatabasePack) = intPreferencesKey("${pack.name}_word_count")

    fun packStateFlow(pack: DatabasePack): Flow<PackInstallState> = store.data.map { prefs ->
        PackInstallState(
            pack = pack,
            isInstalled = prefs[installedKey(pack)] ?: false,
            downloadedBytes = prefs[downloadedBytesKey(pack)] ?: 0L,
            totalBytes = prefs[totalBytesKey(pack)] ?: 0L,
            installedWordCount = prefs[wordCountKey(pack)] ?: 0
        )
    }

    fun allPackStatesFlow(): Flow<List<PackInstallState>> = store.data.map { prefs ->
        DatabasePack.entries.map { pack ->
            PackInstallState(
                pack = pack,
                isInstalled = prefs[installedKey(pack)] ?: false,
                downloadedBytes = prefs[downloadedBytesKey(pack)] ?: 0L,
                totalBytes = prefs[totalBytesKey(pack)] ?: 0L,
                installedWordCount = prefs[wordCountKey(pack)] ?: 0
            )
        }
    }

    suspend fun getDownloadedBytes(pack: DatabasePack): Long =
        store.data.first()[downloadedBytesKey(pack)] ?: 0L

    suspend fun isPackInstalled(pack: DatabasePack): Boolean =
        store.data.first()[installedKey(pack)] ?: false

    suspend fun setDownloadedBytes(pack: DatabasePack, bytes: Long) {
        store.edit { it[downloadedBytesKey(pack)] = bytes }
    }

    suspend fun setTotalBytes(pack: DatabasePack, bytes: Long) {
        store.edit { it[totalBytesKey(pack)] = bytes }
    }

    suspend fun markInstalled(pack: DatabasePack, wordCount: Int) {
        store.edit { prefs ->
            prefs[installedKey(pack)] = true
            prefs[wordCountKey(pack)] = wordCount
            prefs[downloadedBytesKey(pack)] = 0L
        }
    }

    suspend fun clearDownloadProgress(pack: DatabasePack) {
        store.edit { it[downloadedBytesKey(pack)] = 0L }
    }

    suspend fun markUninstalled(pack: DatabasePack) {
        store.edit { prefs ->
            prefs[installedKey(pack)] = false
            prefs[wordCountKey(pack)] = 0
            prefs[downloadedBytesKey(pack)] = 0L
        }
    }
}
