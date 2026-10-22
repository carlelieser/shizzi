package dev.shizzi

import androidx.annotation.StringRes

fun str(@StringRes id: Int, vararg formatArgs: Any): String =
    if (formatArgs.isEmpty()) App.instance.getString(id)
    else App.instance.getString(id, *formatArgs)
