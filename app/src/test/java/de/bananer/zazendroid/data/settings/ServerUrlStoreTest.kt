package de.bananer.zazendroid.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ServerUrlStoreTest {
    private fun store(dir: File, scope: TestScope): ServerUrlStore {
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            File(dir, "settings.preferences_pb")
        }
        return ServerUrlStore(dataStore)
    }

    @Test
    fun `default emitted when absent, stored after save`() = runTest {
        val dir = Files.createTempDirectory("ds").toFile()
        val s = store(dir, TestScope(UnconfinedTestDispatcher()))
        assertEquals(ServerUrlStore.DEFAULT_DATA_SERVER_URL, s.serverUrlFlow().first())
        assertFalse(s.hasStoredUrlFlow().first())
        assertFalse(s.isCustomUrl())

        s.setServerUrl("https://example.com/meditation/")
        assertEquals("https://example.com/meditation", s.serverUrlFlow().first())
        assertTrue(s.hasStoredUrlFlow().first())
        assertTrue(s.isCustomUrl())
    }

    @Test
    fun `invalid and empty urls throw and persist nothing`() = runTest {
        val dir = Files.createTempDirectory("ds").toFile()
        val s = store(dir, TestScope(UnconfinedTestDispatcher()))
        try {
            s.setServerUrl("not a url")
            fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
        try {
            s.setServerUrl("   ")
            fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
        assertFalse(s.isCustomUrl())
    }

    @Test
    fun `normalize trims and strips trailing slash`() {
        assertEquals("https://example.com/m", ServerUrlStore.normalize("  https://example.com/m/  "))
    }
}
