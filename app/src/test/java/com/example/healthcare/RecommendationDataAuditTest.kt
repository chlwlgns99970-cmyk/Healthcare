package com.example.healthcare

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationDataAuditTest {
    @Test
    fun auditCoversEveryStableTemplateAndUsesExplicitCompletenessStates() {
        val rows = readAudit()
        assertEquals(292, rows.size)
        assertEquals(292, rows.map { it.getValue("stableTemplateId") }.distinct().size)
        assertTrue(rows.all { it.getValue("sourceFoodCode").isNotBlank() })
        assertTrue(rows.all { it.getValue("sourceType").isNotBlank() })
        assertTrue(rows.all { it.getValue("sourceUrlOrIdentifier").isNotBlank() })
        assertTrue(rows.all { it.getValue("verifiedAt") == "2026-09-27" })

        val ingredientCounts = rows.groupingBy { it.getValue("ingredientCompleteness") }.eachCount()
        assertEquals(mapOf("COMPLETE" to 36, "PARTIAL" to 1, "UNKNOWN" to 255), ingredientCounts)
        val allergenCounts = rows.groupingBy { it.getValue("allergenCompleteness") }.eachCount()
        assertEquals(mapOf("COMPLETE" to 37, "UNKNOWN" to 255), allergenCounts)
        assertTrue(rows.filter { it.getValue("ingredientCompleteness") != "UNKNOWN" }
            .all { it.getValue("ingredients").isNotBlank() })
        assertTrue(rows.filter { it.getValue("ingredientCompleteness") == "UNKNOWN" }
            .all { it.getValue("notes").contains("UNKNOWN") })
    }

    @Test
    fun officialBrandEvidenceKeepsIngredientAndAllergenClaimsTraceable() {
        val rows = readAudit().associateBy { it.getValue("stableTemplateId") }
        val tlj = rows.getValue("kfind-dinner-convenience-1")
        assertEquals("COMPLETE", tlj.getValue("ingredientCompleteness"))
        assertEquals("COMPLETE", tlj.getValue("allergenCompleteness"))
        assertTrue(tlj.getValue("ingredients").contains("통밀빵"))
        assertTrue(tlj.getValue("sourceUrlOrIdentifier").startsWith("https://www.tlj.co.kr/"))

        val hollys = rows.getValue("kfind-catalog-d214-640000000-0002")
        assertEquals("PARTIAL", hollys.getValue("ingredientCompleteness"))
        assertEquals("COMPLETE", hollys.getValue("allergenCompleteness"))
        assertTrue(hollys.getValue("allergenTags").contains("우유"))
        assertTrue(hollys.getValue("sourceUrlOrIdentifier").startsWith("https://m.hollys.co.kr/"))
    }

    private fun readAudit(): List<Map<String, String>> {
        val root = generateSequence(File(requireNotNull(System.getProperty("user.dir"))).absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }
        val lines = File(root, "data-source/recommendation/recommendation-292-audit.csv").readLines()
        val headers = parseCsvLine(lines.first())
        return lines.drop(1).filter(String::isNotBlank).map { line ->
            headers.zip(parseCsvLine(line)).toMap()
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val value = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            when (val character = line[index]) {
                '"' -> if (quoted && index + 1 < line.length && line[index + 1] == '"') {
                    value.append('"')
                    index++
                } else {
                    quoted = !quoted
                }
                ',' -> if (quoted) value.append(character) else {
                    result += value.toString()
                    value.clear()
                }
                else -> value.append(character)
            }
            index++
        }
        result += value.toString()
        return result
    }
}
