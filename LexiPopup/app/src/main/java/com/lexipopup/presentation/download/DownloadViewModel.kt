package com.lexipopup.presentation.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.lexipopup.data.download.DatabasePack
import com.lexipopup.data.download.DictionaryDownloadWorker
import com.lexipopup.data.download.DownloadStateStore
import com.lexipopup.domain.repositories.DictionaryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class DownloadStatus {
    NOT_INSTALLED, QUEUED, DOWNLOADING, VERIFYING, IMPORTING, INSTALLED, FAILED
}

data class PackDownloadUiState(
    val pack: DatabasePack,
    val status: DownloadStatus = DownloadStatus.NOT_INSTALLED,
    val progress: Int = 0,
    val phase: String = "",
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val speedKbps: Int = 0,
    val etaSeconds: Int = -1,
    val wordsImported: Int = 0,
    val installedWordCount: Int = 0,
    val error: String? = null,
    val downloadLog: String = ""
)

@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val workManager: WorkManager,
    private val downloadStateStore: DownloadStateStore,
    private val dictionaryRepository: DictionaryRepository
) : ViewModel() {

    private val _packStates = MutableStateFlow(
        DatabasePack.entries.associateWith { PackDownloadUiState(it) }
    )
    val packStates: StateFlow<Map<DatabasePack, PackDownloadUiState>> = _packStates.asStateFlow()

    val anyInstalled: StateFlow<Boolean> = _packStates.map { states ->
        states.values.any { it.status == DownloadStatus.INSTALLED }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val installedCount: StateFlow<Int> = _packStates.map { states ->
        states.values.count { it.status == DownloadStatus.INSTALLED }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    init {
        DatabasePack.entries.forEach { pack -> observePack(pack) }
    }

    private fun observePack(pack: DatabasePack) {
        val workName = DictionaryDownloadWorker.uniqueWorkName(pack)

        // Combine WorkManager state + DataStore state into UI state
        combine(
            workManager.getWorkInfosForUniqueWorkFlow(workName),
            downloadStateStore.packStateFlow(pack)
        ) { workInfoList, installState ->

            val wi = workInfoList.firstOrNull()
            val installed = installState.isInstalled

            when {
                installed -> PackDownloadUiState(
                    pack = pack,
                    status = DownloadStatus.INSTALLED,
                    progress = 100,
                    installedWordCount = installState.installedWordCount
                )
                wi == null || wi.state == WorkInfo.State.CANCELLED ||
                wi.state == WorkInfo.State.SUCCEEDED && !installed -> PackDownloadUiState(pack = pack)

                wi.state == WorkInfo.State.ENQUEUED -> PackDownloadUiState(
                    pack = pack, status = DownloadStatus.QUEUED, progress = 0
                )

                wi.state == WorkInfo.State.RUNNING -> {
                    val progress = wi.progress.getInt(DictionaryDownloadWorker.KEY_PROGRESS, 0)
                    val phase = wi.progress.getString(DictionaryDownloadWorker.KEY_PHASE) ?: ""
                    val status = when (phase) {
                        DictionaryDownloadWorker.PHASE_DOWNLOADING -> DownloadStatus.DOWNLOADING
                        DictionaryDownloadWorker.PHASE_DECOMPRESS  -> DownloadStatus.VERIFYING
                        DictionaryDownloadWorker.PHASE_VERIFYING   -> DownloadStatus.VERIFYING
                        DictionaryDownloadWorker.PHASE_IMPORTING   -> DownloadStatus.IMPORTING
                        else -> DownloadStatus.DOWNLOADING
                    }
                    PackDownloadUiState(
                        pack = pack,
                        status = status,
                        progress = progress,
                        phase = phase,
                        bytesDownloaded = wi.progress.getLong(DictionaryDownloadWorker.KEY_BYTES_DL, 0L),
                        totalBytes = wi.progress.getLong(DictionaryDownloadWorker.KEY_TOTAL_BYTES, 0L),
                        speedKbps = wi.progress.getInt(DictionaryDownloadWorker.KEY_SPEED_KBPS, 0),
                        etaSeconds = wi.progress.getInt(DictionaryDownloadWorker.KEY_ETA_SECONDS, -1),
                        wordsImported = wi.progress.getInt(DictionaryDownloadWorker.KEY_WORDS_DONE, 0),
                        downloadLog = wi.progress.getString(DictionaryDownloadWorker.KEY_LOG) ?: ""
                    )
                }

                wi.state == WorkInfo.State.FAILED -> PackDownloadUiState(
                    pack = pack,
                    status = DownloadStatus.FAILED,
                    error = wi.outputData.getString(DictionaryDownloadWorker.KEY_ERROR)
                        ?: "Download failed"
                )

                else -> PackDownloadUiState(pack = pack)
            }
        }.onEach { uiState ->
            _packStates.update { it + (pack to uiState) }
        }.launchIn(viewModelScope)
    }

    fun startDownload(pack: DatabasePack) {
        viewModelScope.launch {
            val resumeBytes = downloadStateStore.getDownloadedBytes(pack)
            val request = DictionaryDownloadWorker.buildRequest(pack, resumeBytes)
            workManager.enqueueUniqueWork(
                DictionaryDownloadWorker.uniqueWorkName(pack),
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }

    fun downloadAll() {
        val states = _packStates.value
        DatabasePack.entries.forEach { pack ->
            val state = states[pack] ?: return@forEach
            if (state.status == DownloadStatus.NOT_INSTALLED || state.status == DownloadStatus.FAILED) {
                startDownload(pack)
            }
        }
    }

    fun cancelDownload(pack: DatabasePack) {
        workManager.cancelUniqueWork(DictionaryDownloadWorker.uniqueWorkName(pack))
        viewModelScope.launch {
            downloadStateStore.clearDownloadProgress(pack)
        }
    }

    fun deletePack(pack: DatabasePack) {
        cancelDownload(pack)
        viewModelScope.launch {
            dictionaryRepository.deletePackWords(pack.name.lowercase())
            downloadStateStore.markUninstalled(pack)
        }
    }
}
