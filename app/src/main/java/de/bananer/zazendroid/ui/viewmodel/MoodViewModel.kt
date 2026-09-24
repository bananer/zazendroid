package de.bananer.zazendroid.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.bananer.zazendroid.data.mood.MoodCheckin
import de.bananer.zazendroid.data.mood.MoodRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Check-in steps: three questions, then the history result. */
enum class MoodStep { SLEEP, STRESS, MOOD, RESULT }

/**
 * Holds the in-progress answers (0–100). Ring starts at 0 on the first
 * question, then carries the previous answer's value into Q2/Q3.
 * Save is atomic at the end; afterwards the flow is view-only history.
 */
class MoodViewModel(private val repo: MoodRepository) : ViewModel() {
    val today: StateFlow<MoodCheckin?> =
        repo.todayFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val history: StateFlow<List<MoodCheckin>> =
        repo.last14DaysFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _step = MutableStateFlow(MoodStep.SLEEP)
    val step: StateFlow<MoodStep> = _step.asStateFlow()

    private val _value = MutableStateFlow(0)
    val value: StateFlow<Int> = _value.asStateFlow()

    private var sleep = 0
    private var stress = 0

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    fun setValue(v: Int) {
        _value.update { v.coerceIn(0, 100) }
    }

    fun next() {
        when (_step.value) {
            MoodStep.SLEEP -> {
                sleep = _value.value
                stress = sleep // Q2 ring starts at Q1 value
                _value.update { stress }
                _step.update { MoodStep.STRESS }
            }
            MoodStep.STRESS -> {
                stress = _value.value
                _value.update { stress } // Q3 ring starts at Q2 value
                _step.update { MoodStep.MOOD }
            }
            MoodStep.MOOD -> {
                val mood = _value.value
                _saving.update { true }
                viewModelScope.launch {
                    repo.save(sleep, stress, mood)
                    _saving.update { false }
                    _step.update { MoodStep.RESULT }
                }
            }
            MoodStep.RESULT -> Unit
        }
    }

    fun back() {
        when (_step.value) {
            MoodStep.STRESS -> {
                _value.update { sleep }
                _step.update { MoodStep.SLEEP }
            }
            MoodStep.MOOD -> {
                _value.update { stress }
                _step.update { MoodStep.STRESS }
            }
            MoodStep.SLEEP, MoodStep.RESULT -> Unit
        }
    }

    /** Entry from Home: fresh day starts at Q1, completed day opens history. */
    fun enter(isComplete: Boolean) {
        if (isComplete) {
            _step.update { MoodStep.RESULT }
        } else {
            sleep = 0
            stress = 0
            _value.update { 0 }
            _step.update { MoodStep.SLEEP }
        }
    }
}
