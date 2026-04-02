package com.runanywhere.kotlin_starter_example.data.models.custom

enum class CompanionRelationshipType(
    val displayName: String,
    val roleLabel: String,
) {
    GIRLFRIEND(
        displayName = "Girlfriend",
        roleLabel = "girlfriend",
    ),
    BOYFRIEND(
        displayName = "Boyfriend",
        roleLabel = "boyfriend",
    ),
    PARTNER(
        displayName = "Partner",
        roleLabel = "partner",
    ),
}
