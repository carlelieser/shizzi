package dev.shizzi

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

sealed interface UiText {

    data class Resource(
        @StringRes val id: Int,
        val formatArgs: List<Any> = emptyList(),
    ) : UiText

    data class Plural(
        @PluralsRes val id: Int,
        val quantity: Int,
        val formatArgs: List<Any> = emptyList(),
    ) : UiText

    data class Verbatim(val text: String) : UiText
}

fun UiText.resolve(resources: Resources): String = when (this) {
    is UiText.Resource -> resources.getString(id, *formatArgs.toTypedArray())
    is UiText.Plural -> resources.getQuantityString(id, quantity, *formatArgs.toTypedArray())
    is UiText.Verbatim -> text
}
