package de.bananer.zazendroid.i18n

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * EN (`values/`) is the source of truth; DE (`values-de/`) must define the
 * same string/plural names with the same format arguments.
 */
class TranslationParityTest {
    private fun resDir(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val cand = File(dir, "app/src/main/res")
            if (cand.isDirectory) return cand
            dir = dir.parentFile
        }
        error("app/src/main/res not found")
    }

    private fun strings(file: File): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val out = mutableMapOf<String, String>()
        val nodes = doc.getElementsByTagName("string")
        for (i in 0 until nodes.length) {
            val el = nodes.item(i)
            out[el.attributes.getNamedItem("name").nodeValue] = el.textContent
        }
        return out
    }

    private fun plurals(file: File): Map<String, Map<String, String>> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val out = mutableMapOf<String, MutableMap<String, String>>()
        val nodes = doc.getElementsByTagName("plurals")
        for (i in 0 until nodes.length) {
            val el = nodes.item(i)
            val name = el.attributes.getNamedItem("name").nodeValue
            val items = mutableMapOf<String, String>()
            val kids = el.childNodes
            for (j in 0 until kids.length) {
                val k = kids.item(j)
                if (k.nodeName == "item") {
                    items[k.attributes.getNamedItem("quantity").nodeValue] = k.textContent
                }
            }
            out[name] = items
        }
        return out
    }

    private val formatArg = Regex("%(\\d+\\$)?[sdf]")

    private fun args(s: String): List<String> = formatArg.findAll(s).map { it.value }.sorted().toList()

    @Test
    fun `de strings match en names and format args`() {
        val res = resDir()
        val en = strings(File(res, "values/strings.xml"))
        val de = strings(File(res, "values-de/strings.xml"))
        assertTrue(en.isNotEmpty())
        assertEquals(en.keys, de.keys)
        en.forEach { (name, text) ->
            assertEquals("format args differ for $name", args(text), args(de.getValue(name)))
        }
    }

    @Test
    fun `de plurals match en names quantities and format args`() {
        val res = resDir()
        val en = plurals(File(res, "values/plurals.xml"))
        val de = plurals(File(res, "values-de/plurals.xml"))
        assertTrue(en.isNotEmpty())
        assertEquals(en.keys, de.keys)
        en.forEach { (name, quantities) ->
            val dq = de.getValue(name)
            assertEquals("quantities differ for $name", quantities.keys, dq.keys)
            quantities.forEach { (q, text) ->
                assertEquals("format args differ for $name/$q", args(text), args(dq.getValue(q)))
            }
        }
    }
}
