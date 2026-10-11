package com.github.shekohex.whisperpp.ime

import android.content.Context
import android.os.IBinder
import android.os.LocaleList
import android.view.ContextThemeWrapper
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import com.github.shekohex.whisperpp.R

/** "Discard this recording?" dialog attached to the IME window identified by [imeWindowToken]. */
fun showCancelConfirmationDialog(context: Context, imeWindowToken: IBinder?, onConfirm: () -> Unit) {
    val themedContext = ContextThemeWrapper(context, R.style.Theme_WhisperToInput)
    val dialog = AlertDialog.Builder(themedContext)
        .setTitle(context.getString(R.string.cancel_confirmation_title))
        .setMessage(context.getString(R.string.cancel_confirmation_message))
        .setPositiveButton(context.getString(R.string.cancel_confirmation_confirm)) { _, _ -> onConfirm() }
        .setNegativeButton(context.getString(R.string.cancel_confirmation_dismiss), null)
        .create()
    dialog.window?.let { window ->
        val lp = window.attributes
        lp.token = imeWindowToken
        lp.type = WindowManager.LayoutParams.TYPE_INPUT_METHOD
        window.attributes = lp
    }
    dialog.show()
}

/** Language picker: auto, English, Arabic and the system locales; [onSelected] gets the language code. */
fun showLanguagePopup(context: Context, anchor: View, current: String, onSelected: (String) -> Unit) {
    val systemLocales = LocaleList.getDefault()
    val languages = mutableListOf("auto" to context.getString(R.string.language_auto_label), "en" to "English", "ar" to "Arabic")
    for (i in 0 until systemLocales.size()) {
        val locale = systemLocales.get(i)
        if (languages.none { it.first == locale.language }) {
            languages.add(locale.language to locale.displayLanguage.replaceFirstChar { it.uppercase() })
        }
    }
    val menu = PopupMenu(ContextThemeWrapper(context, R.style.Theme_WhisperToInput), anchor)
    languages.forEachIndexed { i, (code, name) ->
        menu.menu.add(1, i, i, name).apply {
            isCheckable = true
            isChecked = code == current
        }
    }
    menu.menu.setGroupCheckable(1, true, true)
    menu.setOnMenuItemClickListener { item ->
        onSelected(languages[item.itemId].first)
        true
    }
    menu.show()
}
