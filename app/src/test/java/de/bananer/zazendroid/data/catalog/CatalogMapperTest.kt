package de.bananer.zazendroid.data.catalog

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

private const val FIXTURE = """
{
  "catalogVersion": 1,
  "appInfo": {"appName": "Zazen", "description": "Sit daily.", "version": 3},
  "courses": [
    {"id": "c1", "title": "Basics", "description": "Start here",
     "authorName": "Diana Winston", "categoryTitle": "Foundations",
     "units": [
       {"id": "u1", "title": "Breath", "audioUrl": "/audio/breath.mp3", "durationSeconds": 600, "startOfMeditationInSeconds": 30},
       {"id": "u2", "title": "Body", "audioUrl": "https://cdn.example.com/body.mp3"},
       {"id": "u3", "title": "Sound", "audioUrl": "audio/sound.mp3", "durationSeconds": 300}
     ]},
    {"id": "c2", "title": "Sleep", "description": "Rest",
     "units": [
       {"id": "s1", "title": "Drift", "audioUrl": "/audio/drift.mp3"},
       {"id": "s2", "title": "Deep", "audioUrl": "https://cdn.example.com/deep.mp3", "durationSeconds": 900}
     ]}
  ]
}
"""

class CatalogMapperTest {
    @Test
    fun `fixture resolves relative and absolute audio urls`() {
        val catalog = json.decodeFromString(CatalogDto.serializer(), FIXTURE)
            .toDomain("https://example.com/meditation")

        assertEquals(2, catalog.courses.size)
        val basics = catalog.courses[0]
        assertEquals(3, basics.units.size)
        assertEquals("https://example.com/meditation/audio/breath.mp3", basics.units[0].audioUrl)
        assertEquals("https://cdn.example.com/body.mp3", basics.units[1].audioUrl)
        assertEquals("https://example.com/meditation/audio/sound.mp3", basics.units[2].audioUrl)
        assertEquals("Zazen", catalog.appInfo.appName)
        assertEquals("Diana Winston", basics.authorName)
        assertEquals("Foundations", basics.categoryTitle)
        assertEquals(null, catalog.courses[1].authorName)
        assertEquals(30L, basics.units[0].startOfMeditationInSeconds)
        assertEquals(null, basics.units[1].startOfMeditationInSeconds)
        assertEquals(0, basics.units[0].orderIndex)
        assertEquals(1, basics.units[1].orderIndex)
        assertEquals("c1", basics.units[0].courseId)
        assertEquals(600L, basics.units[0].durationSeconds)
        assertNull(basics.units[1].durationSeconds)
    }
    @Test
    fun `absolute urls pass through unchanged with trailing-slash base`() {
        val dto = CatalogDto(
            appInfo = AppInfoDto("A", "D", 1),
            courses = listOf(
                CourseDto("c", "T", "D", units = listOf(UnitDto("u", "U", "https://cdn.example.com/a.mp3"))),
            ),
        )
        val catalog = dto.toDomain("https://example.com/meditation/")
        assertEquals("https://cdn.example.com/a.mp3", catalog.courses[0].units[0].audioUrl)
    }

    @Test
    fun `invalid entries are dropped, empty result is Empty`() {
        val dto = CatalogDto(
            appInfo = AppInfoDto("A", "D", 1),
            courses = listOf(
                CourseDto("", "NoId", "D", units = listOf(UnitDto("u", "U", "/a.mp3"))),
                CourseDto("c2", "", "D", units = listOf(UnitDto("u", "U", "/a.mp3"))),
                CourseDto("c3", "NoUnits", "D", units = emptyList()),
                CourseDto(
                    "c4", "AllBadUnits", "D",
                    units = listOf(UnitDto("", "U", "/a.mp3"), UnitDto("u", "", "/a.mp3"), UnitDto("u", "U", "")),
                ),
            ),
        )
        try {
            dto.toDomain("https://example.com")
            fail("expected CatalogEmptyException")
        } catch (e: CatalogEmptyException) {
            // expected
        }
    }

    @Test
    fun `blank audioUrl fails fast`() {
        try {
            resolveAudioUrl("https://example.com", "  ")
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }
}
