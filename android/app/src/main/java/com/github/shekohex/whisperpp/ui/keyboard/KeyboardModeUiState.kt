package com.github.shekohex.whisperpp.ui.keyboard

import com.github.shekohex.whisperpp.domain.modes.Mode
import com.github.shekohex.whisperpp.domain.modes.ModeResolution
import com.github.shekohex.whisperpp.domain.modes.ModeResolver

/** What the keyboard mode chip and its picker show (§4.10). */
data class KeyboardModeUiState(
    val active: Item,
    /** Why [active] applies: picked with the chip, the editor's app, or the default. */
    val source: ModeResolution.Source,
    /** Modes the picker offers: enabled ones plus the default. */
    val items: List<Item>,
) {
    data class Item(
        val id: String,
        val name: String,
        val icon: Mode.Icon,
        val isDefault: Boolean,
        val available: Boolean,
    )

    companion object {
        /** Resolves the active mode exactly like a session start would ([ModeResolver.pick]). */
        fun resolve(
            modes: List<Mode>,
            selectedModeId: String?,
            packageName: String?,
            isAvailable: (Mode) -> Boolean,
        ): KeyboardModeUiState {
            val (active, source) = ModeResolver.pick(modes, selectedModeId, packageName)
            val items = modes.filter { it.enabled || it.isDefault }.map { it.toItem(isAvailable) }
            return KeyboardModeUiState(active = active.toItem(isAvailable), source = source, items = items)
        }

        private fun Mode.toItem(isAvailable: (Mode) -> Boolean) = Item(
            id = id,
            name = name,
            icon = icon,
            isDefault = isDefault,
            available = isAvailable(this),
        )
    }
}
