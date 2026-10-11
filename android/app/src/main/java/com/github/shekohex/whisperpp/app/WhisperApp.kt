package com.github.shekohex.whisperpp.app

import android.app.Application
import android.content.Context
import com.github.shekohex.whisperpp.di.AppContainer

class WhisperApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as WhisperApp).container
