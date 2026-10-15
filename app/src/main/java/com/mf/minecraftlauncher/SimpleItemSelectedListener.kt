package com.mf.minecraftlauncher

import android.view.View
import android.widget.AdapterView

/** Bridges Spinner selection events to a small, testable Kotlin callback. */
class SimpleItemSelectedListener(
    private val onSelected: (position: Int) -> Unit
) : AdapterView.OnItemSelectedListener {
    override fun onItemSelected(
        parent: AdapterView<*>?,
        view: View?,
        position: Int,
        id: Long
    ) {
        onSelected(position)
    }

    override fun onNothingSelected(parent: AdapterView<*>?) = Unit
}
