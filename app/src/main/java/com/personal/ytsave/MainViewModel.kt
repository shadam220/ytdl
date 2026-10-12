package com.personal.ytsave

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

sealed interface FetchState {
    data object Idle : FetchState
    data object Loading : FetchState
    data class Ready(val meta: VideoMeta) : FetchState
    data class Error(val message: String) : FetchState
}

class MainViewModel : ViewModel() {

    var url by mutableStateOf("")
        private set
    var fetchState by mutableStateOf<FetchState>(FetchState.Idle)
        private set
    var quality by mutableStateOf(Quality.V1080)
        private set

    private var fetchJob: Job? = null

    fun onUrlChange(value: String) {
        url = value
        if (fetchState !is FetchState.Loading) fetchState = FetchState.Idle
    }

    fun selectQuality(q: Quality) {
        quality = q
    }

    /** Accepts a raw link or any shared text that contains one. */
    fun submit(raw: String = url) {
        val link = URL_REGEX.find(raw)?.value
        if (link == null) {
            fetchState = FetchState.Error("That doesn't look like a link.")
            return
        }
        url = link
        fetchJob?.cancel()
        fetchState = FetchState.Loading
        fetchJob = viewModelScope.launch {
            fetchState = try {
                FetchState.Ready(Downloader.fetchInfo(link))
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                FetchState.Error(friendly(t))
            }
        }
    }

    fun download() {
        val ready = fetchState as? FetchState.Ready ?: return
        Downloader.enqueue(ready.meta, quality)
        url = ""
        fetchState = FetchState.Idle
    }

    private fun friendly(t: Throwable): String {
        val last = t.message?.lines()?.lastOrNull { it.isNotBlank() }?.removePrefix("ERROR: ")
        return last?.take(160) ?: "Couldn't load that video."
    }

    private companion object {
        val URL_REGEX = Regex("""https?://[^\s]+""")
    }
}
