package com.pacefit.ai.domain

enum class ProfileSex(val label: String) {
    FEMALE("Femenino"),
    MALE("Masculino"),
    UNSPECIFIED("Prefiero no indicar"),
}

data class HealthProfile(
    val weightKg: Double,
    val heightCm: Double,
    val age: Int,
    val sex: ProfileSex,
    val updatedAt: Long,
)

data class ProfileDraft(
    val weight: String = "",
    val height: String = "",
    val age: String = "",
    val sex: ProfileSex = ProfileSex.UNSPECIFIED,
) {
    companion object {
        fun from(profile: HealthProfile) = ProfileDraft(
            weight = profile.weightKg.toString(),
            height = profile.heightCm.toString(),
            age = profile.age.toString(),
            sex = profile.sex,
        )
    }
}

data class ProfileValidation(
    val profile: HealthProfile?,
    val errors: Map<String, String>,
)

/**
 * Broad input limits protect against typos; they are not clinical reference ranges.
 * No diagnosis, calorie calculation or network transmission is performed in PF-001.
 */
object ProfileValidator {
    fun validate(draft: ProfileDraft, now: Long): ProfileValidation {
        val weight = decimal(draft.weight)
        val height = decimal(draft.height)
        val age = draft.age.trim().toIntOrNull()
        val errors = buildMap {
            if (weight == null || weight !in 10.0..400.0) {
                put("weight", "Ingresa un peso entre 10 y 400 kg.")
            }
            if (height == null || height !in 50.0..250.0) {
                put("height", "Ingresa una estatura entre 50 y 250 cm.")
            }
            if (age == null || age !in 1..120) {
                put("age", "Ingresa una edad entera entre 1 y 120 años.")
            }
        }
        return ProfileValidation(
            profile = if (errors.isEmpty()) {
                HealthProfile(requireNotNull(weight), requireNotNull(height), requireNotNull(age), draft.sex, now)
            } else null,
            errors = errors,
        )
    }

    private fun decimal(value: String): Double? =
        value.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
}
