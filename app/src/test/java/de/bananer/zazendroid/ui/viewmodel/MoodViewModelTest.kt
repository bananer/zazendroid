package de.bananer.zazendroid.ui.viewmodel

import de.bananer.zazendroid.data.mood.MoodCheckin
import de.bananer.zazendroid.data.mood.MoodRepository
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

private class FakeMoodRepo : MoodRepository {
    private val state = MutableStateFlow(mapOf<Long, MoodCheckin>())
    val saves = mutableListOf<Triple<Int, Int, Int>>()

    override fun todayFlow(): Flow<MoodCheckin?> =
        state.map { it[LocalDate.now().toEpochDay()] }

    override fun last14DaysFlow(): Flow<List<MoodCheckin>> =
        state.map { it.values.sortedBy { c -> c.dateEpochDay } }

    override suspend fun save(sleep: Int, stress: Int, mood: Int) {
        saves += Triple(sleep, stress, mood)
        val today = LocalDate.now().toEpochDay()
        state.update { it + (today to MoodCheckin(today, sleep, stress, mood)) }
    }

    override suspend fun saveForDate(date: LocalDate, sleep: Int, stress: Int, mood: Int) {
        state.update { it + (date.toEpochDay() to MoodCheckin(date.toEpochDay(), sleep, stress, mood)) }
    }
}

class MoodViewModelTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @After
    fun teardown() {
        Dispatchers.resetMain()
    }
    @Test
    fun `ring starts at 0 then carries previous value`() = runTest {
        val vm = MoodViewModel(FakeMoodRepo())
        vm.enter(false)
        assertEquals(MoodStep.SLEEP, vm.step.first())
        assertEquals(0, vm.value.first())
        vm.setValue(70)
        vm.next()
        assertEquals(MoodStep.STRESS, vm.step.first())
        assertEquals(70, vm.value.first()) // Q2 starts at Q1 value
        vm.setValue(20)
        vm.next()
        assertEquals(MoodStep.MOOD, vm.step.first())
        assertEquals(20, vm.value.first()) // Q3 starts at Q2 value
    }

    @Test
    fun `back restores previous value`() = runTest {
        val vm = MoodViewModel(FakeMoodRepo())
        vm.enter(false)
        vm.setValue(70)
        vm.next()
        vm.setValue(20)
        vm.back()
        assertEquals(MoodStep.SLEEP, vm.step.first())
        assertEquals(70, vm.value.first())
    }

    @Test
    fun `finish saves atomically and reaches result`() = runTest {
        val repo = FakeMoodRepo()
        val vm = MoodViewModel(repo)
        vm.enter(false)
        vm.setValue(70)
        vm.next()
        vm.setValue(20)
        vm.next()
        vm.setValue(90)
        vm.next()
        assertEquals(listOf(Triple(70, 20, 90)), repo.saves)
        assertEquals(MoodStep.RESULT, vm.step.first())
    }

    @Test
    fun `enter complete opens result directly`() = runTest {
        val vm = MoodViewModel(FakeMoodRepo())
        vm.enter(true)
        assertEquals(MoodStep.RESULT, vm.step.first())
    }
}
