package com.lifehub.app.ui

import com.lifehub.app.R

fun pluginIconRes(icon: String?): Int = when (icon) {
    "ledger" -> R.drawable.ic_plugin_ledger
    "memo" -> R.drawable.ic_plugin_memo
    "travel" -> R.drawable.ic_plugin_travel
    "hello" -> R.drawable.ic_plugin_hello
    else -> R.drawable.ic_plugin_default
}
