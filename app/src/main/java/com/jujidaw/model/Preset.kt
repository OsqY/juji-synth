package com.jujidaw.model

import kotlinx.serialization.Serializable

/**
 * Preset data model representing a complete snapshot of all synth parameters.
 */
@Serializable
data class Preset(
    val id: Long = 0,
    val name: String,
    val category: String = "User",     // Leads, Pads, Bass, FX, Ambient, User
    val description: String = "",
    val isFactory: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),

    // Complete parameter snapshot serialized as JSON
    val parameters: SynthState = SynthState()
)

object PresetCategory {
    const val LEADS = "Leads"
    const val PADS = "Pads"
    const val BASS = "Bass"
    const val FX = "FX"
    const val AMBIENT = "Ambient"
    const val USER = "User"

    val all = listOf(LEADS, PADS, BASS, FX, AMBIENT, USER)
    val factoryCategories = listOf(LEADS, PADS, BASS, FX, AMBIENT)
}
