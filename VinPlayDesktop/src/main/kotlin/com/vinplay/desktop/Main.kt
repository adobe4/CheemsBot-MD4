package com.vinplay.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.vinplay.desktop.ui.App

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "VinPlay Manager",
        state = rememberWindowState(width = 1360.dp, height = 860.dp)
    ) {
        App()
    }
}
