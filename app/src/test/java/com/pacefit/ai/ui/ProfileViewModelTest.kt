package com.pacefit.ai.ui

import androidx.lifecycle.ViewModelStore
import com.pacefit.ai.data.HealthRepository
import com.pacefit.ai.domain.HealthProfile
import com.pacefit.ai.domain.ProfileDraft
import com.pacefit.ai.domain.ProfileSex
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val initial = HealthProfile(70.0, 175.0, 25, ProfileSex.FEMALE, 10)
    private val draft = ProfileDraft("71,5", "175", "25", ProfileSex.FEMALE)

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { store.clear(); Dispatchers.resetMain() }

    private fun viewModel(repository: HealthRepository) =
        ProfileViewModel(repository).also { store.put("profile", it) }

    @Test fun loadsStoredProfileIntoEditor() = runTest(dispatcher) {
        val vm = viewModel(FakeRepository(initial))
        assertFalse(vm.state.value.loaded)
        advanceUntilIdle()
        assertTrue(vm.state.value.loaded)
        assertEquals(initial, vm.state.value.profile)
        assertEquals("70.0", vm.state.value.draft.weight)
    }

    @Test fun databaseEmissionsDoNotOverwriteUnsavedEdits() = runTest(dispatcher) {
        val repository = FakeRepository(initial)
        val vm = viewModel(repository)
        advanceUntilIdle()
        vm.edit(draft)
        repository.value.value = initial.copy(updatedAt = 20)
        advanceUntilIdle()
        assertEquals(draft, vm.state.value.draft)
        assertEquals(20L, vm.state.value.profile!!.updatedAt)
    }

    @Test fun invalidInputNeverReachesStorage() = runTest(dispatcher) {
        val repository = FakeRepository()
        val vm = viewModel(repository)
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()
        assertEquals(0, repository.saves)
        assertEquals(3, vm.state.value.errors.size)
        assertNull(repository.value.value)
    }

    @Test fun savePersistsAndPreventsDuplicateRequests() = runTest(dispatcher) {
        val repository = FakeRepository()
        val vm = viewModel(repository)
        advanceUntilIdle()
        vm.edit(draft)
        vm.save()
        vm.save()
        assertTrue(vm.state.value.saving)
        advanceUntilIdle()
        assertEquals(1, repository.saves)
        assertEquals(71.5, repository.value.value!!.weightKg, 0.001)
        assertFalse(vm.state.value.saving)
        assertNotNull(vm.state.value.message)
    }

    @Test fun deletionResetsProfileAndDraft() = runTest(dispatcher) {
        val repository = FakeRepository(initial)
        val vm = viewModel(repository)
        advanceUntilIdle()
        vm.delete()
        advanceUntilIdle()
        assertNull(repository.value.value)
        assertNull(vm.state.value.profile)
        assertEquals(ProfileDraft(), vm.state.value.draft)
    }

    @Test fun storageFailurePreservesOldProfileAndEdits() = runTest(dispatcher) {
        val repository = FakeRepository(initial).apply { failWrites = true }
        val vm = viewModel(repository)
        advanceUntilIdle()
        vm.edit(draft)
        vm.save()
        advanceUntilIdle()
        assertEquals(initial, repository.value.value)
        assertEquals(initial, vm.state.value.profile)
        assertEquals(draft, vm.state.value.draft)
        assertFalse(vm.state.value.saving)
        assertTrue(vm.state.value.message!!.contains("No se pudo"))
    }

    @Test fun readFailureDisablesEditsAndWrites() = runTest(dispatcher) {
        val repository = FakeRepository().apply { failReads = true }
        val vm = viewModel(repository)
        advanceUntilIdle()
        assertTrue(vm.state.value.storageError)
        vm.edit(draft)
        vm.save()
        advanceUntilIdle()
        assertEquals(ProfileDraft(), vm.state.value.draft)
        assertEquals(0, repository.saves)
    }

    @Test fun deleteFailureKeepsTheStoredProfile() = runTest(dispatcher) {
        val repository = FakeRepository(initial).apply { failWrites = true }
        val vm = viewModel(repository)
        advanceUntilIdle()
        vm.delete()
        advanceUntilIdle()
        assertEquals(initial, vm.state.value.profile)
        assertEquals(initial, repository.value.value)
        assertFalse(vm.state.value.saving)
        assertTrue(vm.state.value.message!!.contains("No se pudo"))
    }

    @Test fun dismissesSuccessMessage() = runTest(dispatcher) {
        val vm = viewModel(FakeRepository(initial))
        advanceUntilIdle()
        vm.delete()
        advanceUntilIdle()
        assertNotNull(vm.state.value.message)
        vm.dismissMessage()
        assertNull(vm.state.value.message)
    }

    private class FakeRepository(profile: HealthProfile? = null) : HealthRepository {
        val value = MutableStateFlow(profile)
        var saves = 0
        var failWrites = false
        var failReads = false
        override fun observe(): Flow<HealthProfile?> =
            if (failReads) flow { throw IOException("Read failure") } else value

        override suspend fun save(profile: HealthProfile) {
            if (failWrites) throw IOException("Write failure")
            saves++
            value.value = profile
        }

        override suspend fun delete() {
            if (failWrites) throw IOException("Delete failure")
            value.value = null
        }
    }
}
