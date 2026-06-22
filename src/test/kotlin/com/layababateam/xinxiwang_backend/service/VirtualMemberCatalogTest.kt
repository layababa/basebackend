package com.layababateam.xinxiwang_backend.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VirtualMemberCatalogTest {
    @Test
    fun parseLinesTrimsMarkdownBulletsAndDeduplicatesNames() {
        val catalog = VirtualMemberCatalog.fromDisplayNames(
            listOf(
                "  * 张三\\_001  ",
                "",
                "张三_001",
                "李四",
            ),
        )

        assertEquals(listOf("张三_001", "李四"), catalog.candidates.map { it.displayName })
    }

    @Test
    fun selectUnusedSkipsUsedVirtualNamesAndRealMemberIds() {
        val catalog = VirtualMemberCatalog.fromDisplayNames(listOf("Alice", "Bob", "Carol", "Dora"))
        val carolId = VirtualMemberCandidate.stableIdFor("Carol")

        val selected = catalog.selectUnused(
            count = 2,
            usedVirtualIds = emptySet(),
            usedVirtualDisplayNames = setOf("Bob"),
            realMemberIds = setOf(carolId),
        )

        assertEquals(listOf("Alice", "Dora"), selected.map { it.displayName })
        assertTrue(selected.all { it.id.startsWith("virtual:") })
    }

    @Test
    fun exposesStableIdThroughParticipantCompatibleAlias() {
        assertEquals(
            VirtualParticipantCatalog.virtualIdFor("lisi_88"),
            VirtualMemberCandidate.stableIdFor("lisi_88"),
        )
    }

    @Test
    fun countMustStayWithinZeroToOneHundred() {
        val catalog = VirtualMemberCatalog.fromDisplayNames(listOf("Alice"))

        assertFailsWith<IllegalArgumentException> {
            catalog.selectUnused(-1, emptySet(), emptySet(), emptySet())
        }
        assertFailsWith<IllegalArgumentException> {
            catalog.selectUnused(101, emptySet(), emptySet(), emptySet())
        }
    }

    @Test
    fun throwsWhenRequestedCountExceedsAvailablePool() {
        val catalog = VirtualMemberCatalog.fromDisplayNames(listOf("Alice"))

        assertEquals(1, catalog.availableCount(emptySet(), emptySet(), emptySet()))
        assertFailsWith<IllegalArgumentException> {
            catalog.selectUnused(2, emptySet(), emptySet(), emptySet())
        }
    }
}
