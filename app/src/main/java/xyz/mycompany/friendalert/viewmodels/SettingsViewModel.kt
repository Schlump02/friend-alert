package xyz.mycompany.friendalert.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.mycompany.friendalert.repository.ContactRepository
import xyz.mycompany.friendalert.utils.GlobalConfigKeys

/**
 * Manages the state and persistence logic for global friend alert settings, 
 * operating exclusively on the Room Database via ContactRepository.
 */
class SettingsViewModel(private val repository: ContactRepository) : ViewModel() {

    // StateFlow to hold the current three values (the source of truth read from DB)
    private val _globalSettings = MutableStateFlow(GlobalFrequencySettings())
    val globalSettings: StateFlow<GlobalFrequencySettings> = _globalSettings

    init {
        loadSettings()
    }

    data class GlobalFrequencySettings(
        val frequentDays: Int = GlobalConfigKeys.DEFAULT_BASIC_FREQUENCY_DAYS,
        val occasionalDays: Int = GlobalConfigKeys.DEFAULT_OCCASIONAL_FREQUENCY_DAYS,
        val rareDays: Int = GlobalConfigKeys.DEFAULT_RARE_FREQUENCY_DAYS
    )

    /** 
     * Loads the current global frequency settings from the Room Database into the StateFlow.
     */
    fun loadSettings() {
        viewModelScope.launch {
            // This reads directly from the DAO via the Repository
            val defaults = repository.getGlobalFrequencyDefaults()
            _globalSettings.value = GlobalFrequencySettings(
                frequentDays = defaults["FREQUENT"]?.toInt() ?: GlobalConfigKeys.DEFAULT_BASIC_FREQUENCY_DAYS,
                occasionalDays = defaults["OCCASIONAL"]?.toInt() ?: GlobalConfigKeys.DEFAULT_OCCASIONAL_FREQUENCY_DAYS,
                rareDays = defaults["RARE"]?.toInt() ?: GlobalConfigKeys.DEFAULT_RARE_FREQUENCY_DAYS
            )
        }
    }

    /** 
     * Called when the user clicks 'Save Settings'. It triggers validation and writes all current UI values to Room.
     */
    fun saveSettings(frequentDays: Int, occasionalDays: Int, rareDays: Int) {
        // This function assumes the incoming parameters (frequentDays, etc.) 
        // are the values currently selected/entered in the Settings Activity's UI.

        viewModelScope.launch {
            try {
                // Update the global setting and propagate change to DB for all contacts
                repository.updateGlobalFrequency("FREQUENT", frequentDays)
                repository.updateGlobalFrequency("OCCASIONAL", occasionalDays)
                repository.updateGlobalFrequency("RARE", rareDays)

                // Since we successfully updated the database, we update our internal state as well.
                _globalSettings.value = GlobalFrequencySettings(frequentDays, occasionalDays, rareDays)

            } catch (e: Exception) {
                throw IllegalStateException("Failed to save global settings in repository.")
            }
        }
    }
}
