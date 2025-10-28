package cn.antares.helldiver_trainer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.antares.helldiver_trainer.BuildKonfig
import cn.antares.helldiver_trainer.data.github.entity.GithubReleaseEntity
import cn.antares.helldiver_trainer.data.github.repository.GithubRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AppViewModel(private val githubRepository: GithubRepository) : ViewModel() {

    sealed interface UpdateState {
        object Idle : UpdateState
        object Checking : UpdateState
        data class NewRelease(val release: GithubReleaseEntity) : UpdateState
        object UpToDate : UpdateState
        data class Error(val message: String) : UpdateState
    }

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    fun checkForUpdates() {
        if (_state.value == UpdateState.Checking) return
        _state.value = UpdateState.Checking
        viewModelScope.launch {
            try {
                val remoteVersion = githubRepository.getLatestRelease()
                if (isRemoteVersionNewer(remoteVersion.tagName)) {
                    _state.value = UpdateState.NewRelease(remoteVersion)
                } else {
                    _state.value = UpdateState.UpToDate
                }
            } catch (e: Exception) {
                _state.value = UpdateState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun isRemoteVersionNewer(remote: String?): Boolean {
        if (remote.isNullOrBlank()) return false
        val current = BuildKonfig.VERSION_NAME
        fun parse(v: String) = v.trim().removePrefix("v").split('.').mapNotNull { it.toIntOrNull() }
        val a = parse(current)
        val b = parse(remote)
        val n = maxOf(a.size, b.size)
        for (i in 0 until n) {
            val ai = a.getOrNull(i) ?: 0
            val bi = b.getOrNull(i) ?: 0
            if (bi > ai) return true
            if (bi < ai) return false
        }
        return false
    }
}