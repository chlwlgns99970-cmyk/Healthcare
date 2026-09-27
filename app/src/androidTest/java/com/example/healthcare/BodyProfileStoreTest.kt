package com.example.healthcare

import androidx.test.platform.app.InstrumentationRegistry
import com.example.healthcare.data.BodyProfileStore
import com.example.healthcare.data.ExerciseWeightStore
import com.example.healthcare.domain.BodyProfile
import com.example.healthcare.domain.BodySex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyProfileStoreTest {
    @Test fun profileSurvivesStoreRecreationAndSharesWeightWithExerciseCoach() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.example.healthcare.qa", context.packageName)
        val profile = BodyProfile(BodySex.FEMALE, 42, 163.5, 58.2)

        BodyProfileStore(context).save(profile)
        val restored = BodyProfileStore(context).read()

        assertEquals(profile, restored)
        assertEquals(58.2, ExerciseWeightStore(context).read() ?: 0.0, 0.0)

        BodyProfileStore(context).saveExerciseWeight(61.4)
        assertEquals(61.4, BodyProfileStore(context).read()?.weightKg ?: 0.0, 0.0)
        assertEquals(61.4, ExerciseWeightStore(context).read() ?: 0.0, 0.0)
    }

    @Test fun partialExistingProfileIsKeptAsDraftAndDoesNotCountAsComplete() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("body_profile", 0).edit()
            .clear()
            .putString("sex", BodySex.FEMALE.name)
            .putString("height_cm", "163.5")
            .commit()
        context.getSharedPreferences("exercise_coach", 0).edit()
            .putString("weight_kg", "58.2")
            .commit()

        val store = BodyProfileStore(context)
        val draft = store.readDraft()

        assertEquals(BodySex.FEMALE, draft.sex)
        assertNull(draft.ageYears)
        assertEquals(163.5, draft.heightCm ?: 0.0, 0.0)
        assertEquals(58.2, draft.weightKg ?: 0.0, 0.0)
        assertNull(store.read())

        // Leave a valid QA-only profile for later app-level tests regardless of test order.
        store.save(BodyProfile(BodySex.FEMALE, 42, 163.5, 58.2))
    }

    @Test fun validCanonicalProfileWinsOverLegacyExerciseWeight() = withCleanProfileStores { context ->
        val canonical = BodyProfile(BodySex.FEMALE, 42, 163.5, 58.2)
        BodyProfileStore(context).save(canonical)
        context.getSharedPreferences("exercise_coach", 0).edit()
            .putString("weight_kg", "91.0")
            .commit()

        assertEquals(canonical, BodyProfileStore(context).read())
        assertEquals(
            "58.2",
            context.getSharedPreferences("body_profile", 0).getString("weight_kg", null)
        )
    }

    @Test fun legacyExerciseWeightMigratesOnceWithoutInventingMissingFields() =
        withCleanProfileStores { context ->
            context.getSharedPreferences("exercise_coach", 0).edit()
                .putString("weight_kg", "58.2")
                .commit()

            val firstStore = BodyProfileStore(context)
            val firstDraft = firstStore.readDraft()

            assertNull(firstDraft.sex)
            assertNull(firstDraft.ageYears)
            assertNull(firstDraft.heightCm)
            assertEquals(58.2, firstDraft.weightKg ?: 0.0, 0.0)
            assertNull(firstStore.read())
            assertEquals(
                "58.2",
                context.getSharedPreferences("body_profile", 0).getString("weight_kg", null)
            )

            context.getSharedPreferences("exercise_coach", 0).edit()
                .putString("weight_kg", "73.0")
                .commit()

            assertEquals(58.2, BodyProfileStore(context).readDraft().weightKg ?: 0.0, 0.0)
        }

    @Test fun legacyWeightCompletesAnOtherwiseValidCanonicalDraftAndBypassesOnboarding() =
        withCleanProfileStores { context ->
            context.getSharedPreferences("body_profile", 0).edit()
                .putString("sex", BodySex.MALE.name)
                .putInt("age_years", 35)
                .putString("height_cm", "175.4")
                .putString("unrelated_marker", "keep")
                .commit()
            context.getSharedPreferences("exercise_coach", 0).edit()
                .putString("weight_kg", "70.4")
                .putString("unrelated_marker", "keep")
                .commit()

            val migrated = BodyProfileStore(context).read()

            assertEquals(BodyProfile(BodySex.MALE, 35, 175.4, 70.4), migrated)
            assertEquals(
                "keep",
                context.getSharedPreferences("body_profile", 0)
                    .getString("unrelated_marker", null)
            )
            assertEquals(
                "keep",
                context.getSharedPreferences("exercise_coach", 0)
                    .getString("unrelated_marker", null)
            )
        }

    @Test fun invalidLegacyWeightIsNotCorrectedOrMigrated() = withCleanProfileStores { context ->
        context.getSharedPreferences("exercise_coach", 0).edit()
            .putString("weight_kg", "999.0")
            .commit()

        val store = BodyProfileStore(context)

        assertNull(store.readDraft().weightKg)
        assertNull(store.read())
        assertFalse(context.getSharedPreferences("body_profile", 0).contains("weight_kg"))
        assertEquals(
            "999.0",
            context.getSharedPreferences("exercise_coach", 0).getString("weight_kg", null)
        )
    }

    @Test fun newUserWithoutCanonicalOrLegacyProfileStillNeedsOnboarding() =
        withCleanProfileStores { context ->
            val store = BodyProfileStore(context)

            assertEquals(0, listOfNotNull(
                store.readDraft().sex,
                store.readDraft().ageYears,
                store.readDraft().heightCm,
                store.readDraft().weightKg
            ).size)
            assertNull(store.read())
            assertFalse(context.getSharedPreferences("body_profile", 0).contains("weight_kg"))
        }

    private inline fun withCleanProfileStores(block: (android.content.Context) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.example.healthcare.qa", context.packageName)
        val bodyPreferences = context.getSharedPreferences("body_profile", 0)
        val exercisePreferences = context.getSharedPreferences("exercise_coach", 0)
        bodyPreferences.edit().clear().commit()
        exercisePreferences.edit().clear().commit()
        try {
            block(context)
        } finally {
            bodyPreferences.edit().clear().commit()
            exercisePreferences.edit().clear().commit()
            BodyProfileStore(context).save(BodyProfile(BodySex.FEMALE, 42, 163.5, 58.2))
            assertTrue(BodyProfileStore(context).read() != null)
        }
    }
}
