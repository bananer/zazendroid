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
  "categories": [
    {"id": "cat-sleep", "title": "Sleep", "prio": 5},
    {"id": "cat-basics", "title": "Foundations", "prio": 10},
    {"id": "", "title": "Bad", "prio": 1},
    {"id": "cat-bad", "title": "", "prio": 1}
  ],
  "courses": [
    {"id": "c1", "title": "Basics", "description": "Start here",
     "authorName": "Diana Winston", "categoryId": "cat-basics",
     "units": [
       {"id": "u1", "title": "Breath", "audioUrl": "/audio/breath.mp3", "durationSeconds": 600, "startOfMeditationInSeconds": 30},
       {"id": "u2", "title": "Body", "audioUrl": "https://cdn.example.com/body.mp3"},
       {"id": "u3", "title": "Sound", "audioUrl": "audio/sound.mp3", "durationSeconds": 300}
     ]},
    {"id": "c2", "title": "Sleep", "description": "Rest", "categoryId": "cat-sleep",
     "units": [
       {"id": "s1", "title": "Drift", "audioUrl": "/audio/drift.mp3"},
       {"id": "s2", "title": "Deep", "audioUrl": "https://cdn.example.com/deep.mp3", "durationSeconds": 900}
     ]},
    {"id": "c3", "title": "Only bad", "description": "Rest",
     "units": [
       {"id": "", "title": "Bad", "audioUrl": "/audio/bad.mp3"}
     ]}
  ],
  "singles": [
    {"id": "s1", "title": "Quick Breath", "description": "Reset fast",
     "authorName": "Diana Winston", "categoryId": "cat-basics",
     "durationSeconds": 300, "audioUrl": "/audio/quick-breath.mp3"},
    {"id": "s2", "title": "Absolute Calm", "description": "Slow down",
     "audioUrl": "https://cdn.example.com/calm.mp3"},
    {"id": "", "title": "Bad", "audioUrl": "/audio/bad.mp3"},
    {"id": "s3", "title": "", "audioUrl": "/audio/bad.mp3"},
    {"id": "s4", "title": "Silent", "audioUrl": ""}
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
        assertEquals("cat-basics", basics.categoryId)
        assertEquals("Foundations", catalog.categoryTitle("cat-basics"))
        assertEquals(listOf("cat-sleep", "cat-basics"), catalog.categories.map { it.id })
        assertEquals(null, catalog.courses[1].authorName)
        assertEquals("cat-sleep", catalog.courses[1].categoryId)
        assertEquals(30L, basics.units[0].startOfMeditationInSeconds)
        assertEquals(null, basics.units[1].startOfMeditationInSeconds)
        assertEquals(0, basics.units[0].orderIndex)
        assertEquals(1, basics.units[1].orderIndex)
        assertEquals("c1", basics.units[0].courseId)
        assertEquals(600L, basics.units[0].durationSeconds)
        assertNull(basics.units[1].durationSeconds)
        assertEquals(2, catalog.singles.size)
        val quick = catalog.singles[0]
        assertEquals("s1", quick.id)
        assertEquals("Quick Breath", quick.title)
        assertEquals("Reset fast", quick.description)
        assertEquals("Diana Winston", quick.authorName)
        assertEquals("cat-basics", quick.categoryId)
        assertEquals(300L, quick.durationSeconds)
        assertEquals("https://example.com/meditation/audio/quick-breath.mp3", quick.audioUrl)
        assertEquals("https://cdn.example.com/calm.mp3", catalog.singles[1].audioUrl)
        assertNull(catalog.singles[1].durationSeconds)
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
            singles = listOf(
                SingleDto("", "NoId", "D", audioUrl = "/a.mp3"),
                SingleDto("s2", "", "D", audioUrl = "/a.mp3"),
                SingleDto("s3", "NoAudio", "D", audioUrl = ""),
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
    fun `singles-only catalog is valid`() {
        val dto = CatalogDto(
            appInfo = AppInfoDto("A", "D", 1),
            singles = listOf(SingleDto("s1", "Quick", "Fast reset", audioUrl = "/a.mp3")),
        )
        val catalog = dto.toDomain("https://example.com")
        assertEquals(0, catalog.courses.size)
        assertEquals(1, catalog.singles.size)
        assertEquals("https://example.com/a.mp3", catalog.singles[0].audioUrl)
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
