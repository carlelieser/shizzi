package dev.shizzi

object L10n {
    fun string(id: Int, vararg formatArgs: Any): String =
        App.instance.getString(id, *formatArgs)

    fun quantity(id: Int, quantity: Int, vararg formatArgs: Any): String =
        App.instance.resources.getQuantityString(id, quantity, *formatArgs)
}
