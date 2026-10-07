package com.example.healthcare

import com.example.healthcare.domain.AllergyNoticeKind
import com.example.healthcare.domain.AllergyNoticePolicy
import org.junit.Assert.*
import org.junit.Test

class AllergyNoticePolicyTest {
    @Test fun unconfiguredUserGetsNoRepeatedUnknownOrConfirmedNotice() {
        assertEquals(AllergyNoticeKind.NONE, AllergyNoticePolicy.resolve(emptySet(), setOf("우유"), false, true).kind)
        assertNull(AllergyNoticePolicy.resolve(emptySet(), emptySet(), false, false).text)
    }

    @Test fun configuredIntersectionUsesExistingCanonicalEggCause() {
        val notice = AllergyNoticePolicy.resolve(setOf("계란"), setOf("난류", "우유"), true, false)
        assertEquals(AllergyNoticeKind.CONFIRMED, notice.kind)
        assertEquals(setOf("달걀"), notice.matchedCauses)
        assertEquals("알레르기 주의 · 달걀 포함", notice.text)
    }

    @Test fun confirmedNoIntersectionHasNoExtraNotice() {
        assertNull(AllergyNoticePolicy.resolve(setOf("우유"), setOf("밀", "대두"), true, true).text)
        assertNull(AllergyNoticePolicy.resolve(setOf("우유"), emptySet(), true, true).text)
    }

    @Test fun unknownIsOnlyCautionedAtConfiguredUsersDecisionPoint() {
        assertEquals(AllergyNoticeKind.NONE, AllergyNoticePolicy.resolve(setOf("우유"), setOf("UNKNOWN"), false, false).kind)
        val notice = AllergyNoticePolicy.resolve(setOf("우유"), setOf("UNKNOWN"), false, true)
        assertEquals(AllergyNoticeKind.UNRESOLVED, notice.kind)
        assertEquals("알레르기 정보 확인 필요", notice.text)
        assertFalse(notice.text!!.contains("없음"))
    }

    @Test fun knownAllergenStillWarnsWhenOtherAllergenFieldsAreUnknown() {
        val notice = AllergyNoticePolicy.resolve(setOf("밀"), setOf("밀", "UNKNOWN"), false, true)
        assertEquals(AllergyNoticeKind.CONFIRMED, notice.kind)
    }

    @Test fun officialMayContainIsDifferentFromConfirmedIngredient() {
        val notice = AllergyNoticePolicy.resolve(setOf("땅콩"), emptySet(), true, false, setOf("땅콩"))
        assertEquals(AllergyNoticeKind.CROSS_CONTACT, notice.kind)
        assertEquals("알레르기 교차접촉 주의 · 땅콩", notice.text)
        assertFalse(notice.text!!.contains("포함"))
    }

    @Test fun simultaneousDirectAndDifferentContactCausesAreBothShown() {
        val notice = AllergyNoticePolicy.resolve(setOf("우유", "땅콩"), setOf("우유"), true, true,
            setOf("우유", "땅콩"))
        assertEquals(AllergyNoticeKind.CONFIRMED, notice.kind)
        assertEquals(setOf("우유"), notice.matchedCauses)
        assertEquals(setOf("땅콩"), notice.crossContactCauses)
        assertTrue(notice.text!!.contains("우유 포함"))
        assertTrue(notice.text!!.contains("교차접촉 주의 · 땅콩"))
    }

    @Test fun displayedCausesAreDeterministicAndNeverIncludeUnknownMarker() {
        val first = AllergyNoticePolicy.resolve(linkedSetOf("우유", "밀"), setOf("UNKNOWN", "우유", "밀"), false, true)
        val second = AllergyNoticePolicy.resolve(linkedSetOf("밀", "우유"), setOf("밀", "우유"), false, true)
        assertEquals(first.text, second.text)
        assertFalse(first.text!!.contains("UNKNOWN"))
    }

    @Test fun explicitUnknownMarkerCannotBeMadeSafeByContradictoryCompletenessFlag() {
        assertEquals(AllergyNoticeKind.UNRESOLVED,
            AllergyNoticePolicy.resolve(setOf("우유"), setOf("UNKNOWN"), true, true).kind)
    }
}
