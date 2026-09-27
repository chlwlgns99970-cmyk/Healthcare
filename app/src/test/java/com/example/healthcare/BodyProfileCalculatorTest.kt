package com.example.healthcare

import com.example.healthcare.domain.BodyProfile
import com.example.healthcare.domain.BodyProfileCalculator
import com.example.healthcare.domain.BodySex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyProfileCalculatorTest {
    @Test fun mifflinStJeorUsesSexAgeHeightAndWeight() {
        val male = BodyProfile(BodySex.MALE, 35, 175.0, 70.4)
        val female = male.copy(sex = BodySex.FEMALE)
        assertEquals(1628, BodyProfileCalculator.estimateBmr(male))
        assertEquals(1462, BodyProfileCalculator.estimateBmr(female))
    }

    @Test fun adultProfileValidationRejectsMissingAndImplausibleValues() {
        val missing = BodyProfileCalculator.validate(null, "", "", "")
        assertFalse(missing.isValid)
        assertNull(missing.profile)
        assertEquals("성별을 선택해 주세요.", missing.sexError)

        val invalid = BodyProfileCalculator.validate(BodySex.MALE, "17", "251", "0")
        assertFalse(invalid.isValid)
        assertTrue(invalid.ageError.orEmpty().contains("18세"))
        assertTrue(invalid.heightError.orEmpty().contains("250cm"))
        assertTrue(invalid.weightError.orEmpty().contains("25kg"))

        assertTrue(BodyProfileCalculator.validate(BodySex.FEMALE, "35", "165.5", "58,2").isValid)
    }
}
