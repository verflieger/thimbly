package at.mtr.sewfar

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element

/** SPEC 12: every translatable string exists in English (values) and German (values-de), and nothing extra. */
class StringsCompletenessTest {
    private fun names(dir: String): Set<String> {
        val files = File("src/main/res/$dir").listFiles { f -> f.name.startsWith("strings") && f.extension == "xml" }.orEmpty()
        return files.flatMap { file ->
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            listOf("string", "plurals", "string-array").flatMap { tag ->
                val nodes = doc.getElementsByTagName(tag)
                (0 until nodes.length).map { nodes.item(it) as Element }
                    .filter { it.getAttribute("translatable") != "false" }
                    .map { "$tag/${it.getAttribute("name")}" }
            }
        }.toSet()
    }

    @Test
    fun germanMatchesEnglish() {
        val english = names("values")
        val german = names("values-de")
        assertEquals("Missing in values-de", emptySet<String>(), english - german)
        assertEquals("Only in values-de", emptySet<String>(), german - english)
    }
}
