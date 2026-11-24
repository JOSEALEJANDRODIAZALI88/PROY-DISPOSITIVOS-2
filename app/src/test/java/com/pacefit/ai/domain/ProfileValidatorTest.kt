package com.pacefit.ai.domain

import org.junit.Assert.*
import org.junit.Test

class ProfileValidatorTest {
    private val valid = ProfileDraft("70", "175", "25", ProfileSex.FEMALE)

    @Test fun acceptsValidProfileAndTimestamp() {
        val result = ProfileValidator.validate(valid, 123L)
        assertTrue(result.errors.isEmpty())
        assertEquals(HealthProfile(70.0, 175.0, 25, ProfileSex.FEMALE, 123L), result.profile)
    }

    @Test fun acceptsSpanishDecimalsAndWhitespace() {
        val profile = ProfileValidator.validate(valid.copy(weight = " 70,5 ", height = "175,2"), 0).profile!!
        assertEquals(70.5, profile.weightKg, 0.001)
        assertEquals(175.2, profile.heightCm, 0.001)
    }

    @Test fun rejectsEmptyFields() {
        val result = ProfileValidator.validate(ProfileDraft(), 0)
        assertNull(result.profile)
        assertEquals(setOf("weight", "height", "age"), result.errors.keys)
    }

    @Test fun rejectsNonFiniteAndMalformedNumbers() {
        listOf("NaN", "Infinity", "-Infinity", "1e999", "texto", "12,3,4").forEach {
            assertTrue("$it must fail", "weight" in ProfileValidator.validate(valid.copy(weight = it), 0).errors)
        }
    }

    @Test fun enforcesWeightBounds() {
        listOf("0", "-1", "9.9", "400.1").forEach {
            assertTrue("weight" in ProfileValidator.validate(valid.copy(weight = it), 0).errors)
        }
        listOf("10", "400").forEach {
            assertNotNull(ProfileValidator.validate(valid.copy(weight = it), 0).profile)
        }
    }

    @Test fun enforcesHeightBounds() {
        listOf("0", "49.9", "250.1", "NaN").forEach {
            assertTrue("height" in ProfileValidator.validate(valid.copy(height = it), 0).errors)
        }
    }

    @Test fun requiresIntegerAgeInBounds() {
        listOf("0", "-1", "121", "25.5", "NaN", "").forEach {
            assertTrue("age" in ProfileValidator.validate(valid.copy(age = it), 0).errors)
        }
    }

    @Test fun unspecifiedSexIsAllowed() {
        assertEquals(ProfileSex.UNSPECIFIED,
            ProfileValidator.validate(valid.copy(sex = ProfileSex.UNSPECIFIED), 0).profile!!.sex)
    }

    @Test fun draftRoundTripPreservesValues() {
        val original = HealthProfile(65.7, 168.5, 32, ProfileSex.MALE, 45)
        assertEquals(original, ProfileValidator.validate(ProfileDraft.from(original), 45).profile)
    }
}
