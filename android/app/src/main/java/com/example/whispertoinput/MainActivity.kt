/*
 * This file is part of Whisper To Input, see <https://github.com/j3soon/whisper-to-input>.
 *
 * Copyright (c) 2023-2025 Yan-Bin Diau, Johnson Sun
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.example.whispertoinput

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.core.app.ActivityCompat
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.*
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.widget.doOnTextChanged
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.color.DynamicColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

// 200 and 201 are an arbitrary values, as long as they do not conflict with each other
private const val MICROPHONE_PERMISSION_REQUEST_CODE = 200
private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 201
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
val SPEECH_TO_TEXT_BACKEND = stringPreferencesKey("speech-to-text-backend")
val ENDPOINT = stringPreferencesKey("endpoint")
val LANGUAGE_CODE = stringPreferencesKey("language-code")
val API_KEY = stringPreferencesKey("api-key")
val MODEL = stringPreferencesKey("model")
val AUTO_RECORDING_START = booleanPreferencesKey("is-auto-recording-start")
val AUTO_SWITCH_BACK = booleanPreferencesKey("auto-switch-back")
val CANCEL_CONFIRMATION = booleanPreferencesKey("cancel-confirmation")
val ADD_TRAILING_SPACE = booleanPreferencesKey("add-trailing-space")
val POSTPROCESSING = stringPreferencesKey("postprocessing")
val TIMEOUT = intPreferencesKey("timeout")
val PROMPT = stringPreferencesKey("prompt")
val USE_CONTEXT = booleanPreferencesKey("use-context")

val SMART_FIX_ENABLED = booleanPreferencesKey("smart-fix-enabled")
val SMART_FIX_BACKEND = stringPreferencesKey("smart-fix-backend")
val SMART_FIX_ENDPOINT = stringPreferencesKey("smart-fix-endpoint")
val SMART_FIX_API_KEY = stringPreferencesKey("smart-fix-api-key")
val SMART_FIX_MODEL = stringPreferencesKey("smart-fix-model")
val SMART_FIX_TEMPERATURE = floatPreferencesKey("smart-fix-temperature")
val SMART_FIX_PROMPT = stringPreferencesKey("smart-fix-prompt")

class MainActivity : AppCompatActivity() {
    private var setupSettingItemsDone: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DynamicColors.applyToActivityIfAvailable(this)
        setContentView(R.layout.activity_main)
        setupSettingItems()
        checkPermissions()
    }

    // The onClick event of the grant permission button.
    // Opens up the app settings panel to manually configure permissions.
    fun onRequestMicrophonePermission(view: View) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        with(intent) {
            data = Uri.fromParts("package", packageName, null)
            addCategory(Intent.CATEGORY_DEFAULT)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        }

        startActivity(intent)
    }

    // Checks whether permissions are granted. If not, automatically make a request.
    private fun checkPermissions() {
        val permission_and_code = arrayOf(
            Pair(Manifest.permission.RECORD_AUDIO, MICROPHONE_PERMISSION_REQUEST_CODE),
            Pair(Manifest.permission.POST_NOTIFICATIONS, NOTIFICATION_PERMISSION_REQUEST_CODE),
        )
        for ((permission, code) in permission_and_code) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    permission
                ) == PackageManager.PERMISSION_DENIED
            ) {
                // Shows a popup for permission request.
                // If the permission has been previously (hard-)denied, the popup will not show.
                // onRequestPermissionsResult will be called in either case.
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(permission),
                    code
                )
            }
        }
    }

    // Handles the results of permission requests.
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        var msg: String

        // Only handles requests marked with the unique code.
        if (requestCode == MICROPHONE_PERMISSION_REQUEST_CODE) {
            msg = getString(R.string.mic_permission_required)
        } else if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            msg = getString(R.string.notification_permission_required)
        } else {
            return
        }

        // All permissions should be granted.
        for (result in grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                return
            }
        }
    }

    // Below are settings related functions
    abstract inner class SettingItem() {
        protected var isDirty: Boolean = false
        abstract fun setup() : Job
        abstract suspend fun apply()
        protected suspend fun <T> readSetting(key: Preferences.Key<T>): T? {
            // work is moved to `Dispatchers.IO` under the hood
            // Ref: https://developer.android.com/codelabs/android-preferences-datastore#3
            return dataStore.data.map { preferences ->
                preferences[key]
            }.first()
        }
        protected suspend fun <T> writeSetting(key: Preferences.Key<T>, newValue: T) {
            // work is moved to `Dispatchers.IO` under the hood
            // Ref: https://developer.android.com/codelabs/android-preferences-datastore#3
            dataStore.edit { settings ->
                settings[key] = newValue
            }
        }
    }

    inner class SettingText(
        private val viewId: Int,
        private val preferenceKey: Preferences.Key<String>,
        private val defaultValue: String = ""
    ): SettingItem() {
        override fun setup(): Job {
            return CoroutineScope(Dispatchers.Main).launch {
                val btnApply: Button = findViewById(R.id.btn_settings_apply)
                val editText = findViewById<EditText>(viewId)
                editText.isEnabled = false
                editText.doOnTextChanged { _, _, _, _ ->
                    if (!setupSettingItemsDone) return@doOnTextChanged
                    isDirty = true
                    btnApply.isEnabled = true
                }

                // Read data. If none, apply default value.
                val settingValue: String? = readSetting(preferenceKey)
                val value: String = settingValue ?: defaultValue
                if (settingValue == null) {
                    writeSetting(preferenceKey, defaultValue)
                }
                editText.setText(value)
                editText.isEnabled = true
            }
        }
        override suspend fun apply() {
            if (!isDirty) return
            val newValue: String = findViewById<EditText>(viewId).text.toString()
            writeSetting(preferenceKey, newValue)
            isDirty = false
        }
    }

    inner class SettingSwitch(
        private val viewId: Int,
        private val preferenceKey: Preferences.Key<Boolean>,
        private val defaultValue: Boolean = true
    ): SettingItem() {
        override fun setup(): Job {
            return CoroutineScope(Dispatchers.Main).launch {
                val btnApply: Button = findViewById(R.id.btn_settings_apply)
                val switch = findViewById<MaterialSwitch>(viewId)
                switch.isEnabled = false

                switch.setOnCheckedChangeListener { _, _ ->
                    if (!setupSettingItemsDone) return@setOnCheckedChangeListener
                    isDirty = true
                    btnApply.isEnabled = true
                }

                // Read data. If none, apply default value.
                val settingValue: Boolean? = readSetting(preferenceKey)
                val value: Boolean = settingValue ?: defaultValue
                if (settingValue == null) {
                    writeSetting(preferenceKey, defaultValue)
                }
                
                switch.isChecked = value
                switch.isEnabled = true
            }
        }
        override suspend fun apply() {
            if (!isDirty) return
            val switch = findViewById<MaterialSwitch>(viewId)
            val newValue: Boolean = switch.isChecked
            writeSetting(preferenceKey, newValue)
            isDirty = false
        }
    }

    inner class SettingStringDropdown(
        private val viewId: Int,
        private val preferenceKey: Preferences.Key<String>,
        private val optionValues: List<String>,
        private val defaultValue: String = ""
    ): SettingItem() {
        override fun setup(): Job {
            return CoroutineScope(Dispatchers.Main).launch {
                val btnApply: Button = findViewById(R.id.btn_settings_apply)
                val dropdown = findViewById<AutoCompleteTextView>(viewId)
                dropdown.isEnabled = false

                val adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_dropdown_item_1line, optionValues)
                dropdown.setAdapter(adapter)

                dropdown.setOnItemClickListener { parent, _, position, _ ->
                    if (!setupSettingItemsDone) return@setOnItemClickListener
                    isDirty = true
                    btnApply.isEnabled = true
                    
                    // Deal with individual dropdown logic
                    if (viewId == R.id.spinner_speech_to_text_backend) {
                         val selectedItem = parent.getItemAtPosition(position).toString()
                         if (selectedItem == getString(R.string.settings_option_openai_api)) {
                             val endpointEditText = findViewById<EditText>(R.id.field_endpoint)
                             endpointEditText.setText(getString(R.string.settings_option_openai_api_default_endpoint))
                             val modelEditText = findViewById<EditText>(R.id.field_model)
                             modelEditText.setText(getString(R.string.settings_option_openai_api_default_model))
                         } else if (selectedItem == getString(R.string.settings_option_whisper_asr_webservice)) {
                             val endpointEditText = findViewById<EditText>(R.id.field_endpoint)
                             if (endpointEditText.text.isEmpty() ||
                                 endpointEditText.text.toString() == getString(R.string.settings_option_openai_api_default_endpoint) ||
                                 endpointEditText.text.toString() == getString(R.string.settings_option_nvidia_nim_default_endpoint)
                             ) {
                                 endpointEditText.setText(getString(R.string.settings_option_whisper_asr_webservice_default_endpoint))
                             }
                             val modelEditText = findViewById<EditText>(R.id.field_model)
                             modelEditText.setText(getString(R.string.settings_option_whisper_asr_webservice_default_model))
                         } else if (selectedItem == getString(R.string.settings_option_nvidia_nim)) {
                             val endpointEditText = findViewById<EditText>(R.id.field_endpoint)
                             if (endpointEditText.text.isEmpty() ||
                                 endpointEditText.text.toString() == getString(R.string.settings_option_openai_api_default_endpoint) ||
                                 endpointEditText.text.toString() == getString(R.string.settings_option_whisper_asr_webservice_default_endpoint)
                             ) {
                                 endpointEditText.setText(getString(R.string.settings_option_nvidia_nim_default_endpoint))
                             }
                             val modelEditText = findViewById<EditText>(R.id.field_model)
                             modelEditText.setText(getString(R.string.settings_option_nvidia_nim_default_model))
                             val languageCodeEditText = findViewById<EditText>(R.id.field_language_code)
                             languageCodeEditText.setText(getString(R.string.settings_option_nvidia_nim_default_language))
                         }
                    } else if (viewId == R.id.spinner_smart_fix_backend) {
                        val selectedItem = parent.getItemAtPosition(position).toString()
                        val endpointEditText = findViewById<EditText>(R.id.field_smart_fix_endpoint)
                        val modelEditText = findViewById<EditText>(R.id.field_smart_fix_model)

                        if (selectedItem == getString(R.string.settings_smart_fix_backend_openai)) {
                            if (endpointEditText.text.isEmpty() ||
                                endpointEditText.text.toString() == getString(R.string.settings_smart_fix_google_default_endpoint)
                            ) {
                                endpointEditText.setText(getString(R.string.settings_smart_fix_openai_default_endpoint))
                            }
                            if (modelEditText.text.isEmpty() ||
                                modelEditText.text.toString() == getString(R.string.settings_smart_fix_google_default_model)
                            ) {
                                modelEditText.setText(getString(R.string.settings_smart_fix_openai_default_model))
                            }
                        } else if (selectedItem == getString(R.string.settings_smart_fix_backend_google)) {
                            if (endpointEditText.text.isEmpty() ||
                                endpointEditText.text.toString() == getString(R.string.settings_smart_fix_openai_default_endpoint)
                            ) {
                                endpointEditText.setText(getString(R.string.settings_smart_fix_google_default_endpoint))
                            }
                            if (modelEditText.text.isEmpty() ||
                                modelEditText.text.toString() == getString(R.string.settings_smart_fix_openai_default_model)
                            ) {
                                modelEditText.setText(getString(R.string.settings_smart_fix_google_default_model))
                            }
                        }
                    }
                }

                // Read data. If none, apply default value.
                val settingValue: String? = readSetting(preferenceKey)
                val value: String = settingValue ?: defaultValue
                if (settingValue == null) {
                    writeSetting(preferenceKey, defaultValue)
                }
                
                dropdown.setText(value, false)
                dropdown.isEnabled = true
            }
        }
        override suspend fun apply() {
            if (!isDirty) return
            val dropdown = findViewById<AutoCompleteTextView>(viewId)
            val newValue: String = dropdown.text.toString()
            writeSetting(preferenceKey, newValue)
            isDirty = false
        }
    }

    inner class SettingTimeout(
        private val sliderId: Int,
        private val fieldId: Int,
        private val preferenceKey: Preferences.Key<Int>,
        private val defaultValue: Int = 10000
    ): SettingItem() {
        override fun setup(): Job {
            return CoroutineScope(Dispatchers.Main).launch {
                val btnApply: Button = findViewById(R.id.btn_settings_apply)
                val slider = findViewById<Slider>(sliderId)
                val editText = findViewById<EditText>(fieldId)
                
                slider.isEnabled = false
                editText.isEnabled = false

                slider.addOnChangeListener { _, value, fromUser ->
                    if (!setupSettingItemsDone || !fromUser) return@addOnChangeListener
                    editText.setText(value.toInt().toString())
                    isDirty = true
                    btnApply.isEnabled = true
                }

                editText.doOnTextChanged { text, _, _, _ ->
                    if (!setupSettingItemsDone) return@doOnTextChanged
                    val value = text.toString().toIntOrNull()
                    if (value != null) {
                        if (value in slider.valueFrom.toInt()..slider.valueTo.toInt()) {
                            slider.value = value.toFloat()
                        }
                    }
                    isDirty = true
                    btnApply.isEnabled = true
                }

                // Read data. If none, apply default value.
                val settingValue: Int? = readSetting(preferenceKey)
                val value: Int = settingValue ?: defaultValue
                if (settingValue == null) {
                    writeSetting(preferenceKey, defaultValue)
                }
                
                editText.setText(value.toString())
                if (value in slider.valueFrom.toInt()..slider.valueTo.toInt()) {
                    slider.value = value.toFloat()
                }
                
                slider.isEnabled = true
                editText.isEnabled = true
            }
        }
        override suspend fun apply() {
            if (!isDirty) return
            val editText = findViewById<EditText>(fieldId)
            val newValue: Int = editText.text.toString().toIntOrNull() ?: defaultValue
            writeSetting(preferenceKey, newValue)
            isDirty = false
        }
    }

    inner class SettingFloatSlider(
        private val sliderId: Int,
        private val preferenceKey: Preferences.Key<Float>,
        private val defaultValue: Float = 0.0f
    ): SettingItem() {
        override fun setup(): Job {
            return CoroutineScope(Dispatchers.Main).launch {
                val btnApply: Button = findViewById(R.id.btn_settings_apply)
                val slider = findViewById<Slider>(sliderId)
                slider.isEnabled = false

                slider.addOnChangeListener { _, _, fromUser ->
                    if (!setupSettingItemsDone || !fromUser) return@addOnChangeListener
                    isDirty = true
                    btnApply.isEnabled = true
                }

                val settingValue: Float? = readSetting(preferenceKey)
                val value: Float = settingValue ?: defaultValue
                if (settingValue == null) {
                    writeSetting(preferenceKey, defaultValue)
                }
                
                slider.value = value
                slider.isEnabled = true
            }
        }
        override suspend fun apply() {
            if (!isDirty) return
            val slider = findViewById<Slider>(sliderId)
            writeSetting(preferenceKey, slider.value)
            isDirty = false
        }
    }

    private fun setupSettingItems() {
        setupSettingItemsDone = false
        // Add setting items here to apply functions to them
        CoroutineScope(Dispatchers.Main).launch {
            val settingItems = arrayOf(
                SettingStringDropdown(R.id.spinner_speech_to_text_backend, SPEECH_TO_TEXT_BACKEND, listOf(
                    getString(R.string.settings_option_openai_api),
                    getString(R.string.settings_option_whisper_asr_webservice),
                    getString(R.string.settings_option_nvidia_nim)
                ), getString(R.string.settings_option_openai_api)),
                SettingText(R.id.field_endpoint, ENDPOINT, getString(R.string.settings_option_openai_api_default_endpoint)),
                SettingText(R.id.field_language_code, LANGUAGE_CODE, getString(R.string.settings_option_openai_api_default_language)),
                SettingTimeout(R.id.slider_timeout, R.id.field_timeout, TIMEOUT, 10000),
                SettingText(R.id.field_api_key, API_KEY),
                SettingText(R.id.field_model, MODEL, getString(R.string.settings_option_openai_api_default_model)),
                SettingSwitch(R.id.switch_auto_recording_start, AUTO_RECORDING_START),
                SettingSwitch(R.id.switch_auto_switch_back, AUTO_SWITCH_BACK, false),
                SettingSwitch(R.id.switch_confirm_cancel, CANCEL_CONFIRMATION, true),
                SettingSwitch(R.id.switch_add_trailing_space, ADD_TRAILING_SPACE, false),
                SettingStringDropdown(R.id.spinner_postprocessing, POSTPROCESSING, listOf(
                    getString(R.string.settings_option_to_traditional),
                    getString(R.string.settings_option_to_simplified),
                    getString(R.string.settings_option_no_conversion)
                ), getString(R.string.settings_option_to_traditional)),
                SettingText(R.id.field_prompt, PROMPT),
                SettingSwitch(R.id.switch_use_context, USE_CONTEXT, false),
                SettingSwitch(R.id.switch_smart_fix_enabled, SMART_FIX_ENABLED, false),
                SettingStringDropdown(R.id.spinner_smart_fix_backend, SMART_FIX_BACKEND, listOf(
                    getString(R.string.settings_smart_fix_backend_openai),
                    getString(R.string.settings_smart_fix_backend_google)
                ), getString(R.string.settings_smart_fix_backend_openai)),
                SettingText(R.id.field_smart_fix_endpoint, SMART_FIX_ENDPOINT),
                SettingText(R.id.field_smart_fix_api_key, SMART_FIX_API_KEY),
                SettingText(R.id.field_smart_fix_model, SMART_FIX_MODEL),
                SettingFloatSlider(R.id.slider_smart_fix_temperature, SMART_FIX_TEMPERATURE, 0.0f),
                SettingText(R.id.field_smart_fix_prompt, SMART_FIX_PROMPT),
            )
            val btnApply: Button = findViewById(R.id.btn_settings_apply)
            btnApply.isEnabled = false
            btnApply.setOnClickListener {
                CoroutineScope(Dispatchers.Main).launch {
                    btnApply.isEnabled = false
                    for (settingItem in settingItems) {
                        settingItem.apply()
                    }
                    btnApply.isEnabled = false
                }
                Toast.makeText(this@MainActivity, R.string.successfully_set, Toast.LENGTH_SHORT).show()
            }
            settingItems.map { settingItem -> settingItem.setup() }.joinAll()
            
            // Setup help button
            findViewById<View>(R.id.btn_prompt_help).setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://platform.openai.com/docs/guides/speech-to-text#prompting"))
                startActivity(intent)
            }
            
            setupSettingItemsDone = true
        }
    }
}
