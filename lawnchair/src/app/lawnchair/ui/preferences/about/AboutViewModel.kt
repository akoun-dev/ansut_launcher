package app.lawnchair.ui.preferences.about

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import com.android.launcher3.BuildConfig
import com.android.launcher3.R
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.create

class AboutViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val api: GitHubService = gitHubApiRetrofit.create()
    private val prefs: PreferenceManager = PreferenceManager.getInstance(application)
    private val prefs2: PreferenceManager2 = PreferenceManager2.getInstance(application)

    private val nightlyBuildsRepository = NightlyBuildsRepository(
        applicationContext = application,
        api = api,
    )

    val uiState: StateFlow<AboutUiState>
        field = MutableStateFlow(AboutUiState())

    val updateState = nightlyBuildsRepository.updateState

    init {
        uiState.update {
            it.copy(
                versionName = if (prefs.hideVersionInfo.get()) {
                    prefs.pseudonymVersion.get() + " (pseudonym)"
                } else {
                    BuildConfig.VERSION_NAME
                },
                commitHash = BuildConfig.COMMIT_HASH,
                coreTeam = team,
                supportAndPr = supportAndPr,
                topLinks = topLinks,
                bottomLinks = bottomLinks,
            )
        }

        viewModelScope.launch(Dispatchers.Default) {
            val activeContributors = fetchActiveContributors()
            val updatedCoreTeam = uiState.value.coreTeam.map { member ->
                val status = if (member.githubUsername != null && activeContributors.contains(member.githubUsername.lowercase())) ContributorStatus.Active else ContributorStatus.Idle
                member.copy(status = status)
            }
            uiState.update { it.copy(coreTeam = updatedCoreTeam) }
        }

        // Check if the build variant is Nightly
        // AND check if user has enabled auto updater (available to Nightly variant)
        // OR check if user has overridden it in debug flags (available to All variant)
        if (BuildConfig.APPLICATION_ID.contains("nightly") && prefs2.autoUpdaterNightly.firstCached()) {
            nightlyBuildsRepository.checkForUpdate()
            viewModelScope.launch {
                nightlyBuildsRepository.updateState.collect { state ->
                    uiState.update { it.copy(updateState = state) }
                }
            }
        }
    }

    fun downloadUpdate() {
        nightlyBuildsRepository.downloadUpdate()
    }

    fun installUpdate(file: File, forceInstall: Boolean = false) {
        nightlyBuildsRepository.installUpdate(file, forceInstall)
    }

    fun resetToDownloaded(file: File) {
        nightlyBuildsRepository.resetToDownloaded(file)
    }

    private suspend fun fetchActiveContributors(): Set<String> {
        // The ANSUT fork does not track upstream contributor activity.
        return emptySet()
    }

    companion object {
        private val team = listOf(
            TeamMember(
                name = "Équipe ANSUT",
                role = Role.Development,
                photoUrl = "https://avatars.githubusercontent.com/u/0",
                socialUrl = "https://www.ansut.ci",
            ),
        )

        private val topLinks = listOf(
            Link(
                iconResId = R.drawable.ic_help,
                labelResId = R.string.support,
                url = "https://www.ansut.ci",
            ),
            Link(
                iconResId = R.drawable.ic_github,
                labelResId = R.string.github,
                url = "https://github.com/akoun-dev/ansut_launcher",
            ),
        )

        private val bottomLinks = listOf<Link>()

        private val supportAndPr = listOf<TeamMember>()
    }
}
