package com.appcues.data.mapper.trait

import com.appcues.data.mapper.LeveledTraitResponse
import com.appcues.trait.ExperienceTrait
import com.appcues.trait.TraitRegistry
import com.appcues.trait.appcues.BackdropKeyholeTrait

internal class TraitsMapper(
    private val traitRegistry: TraitRegistry
) {

    fun map(from: List<LeveledTraitResponse>): List<ExperienceTrait> {
        return arrayListOf<ExperienceTrait>().apply {
            from.forEach {
                traitRegistry[it.first.type]?.also { factory ->
                    add(factory.invoke(it.first.config, it.second))
                }
            }

            // ensure BackdropKeyholeTrait is applied first
            sortBy { it is BackdropKeyholeTrait }
        }
    }
}
