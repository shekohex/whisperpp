# Deferred Items

- 2026-03-09: `./android/gradlew -p android testDebugUnitTest --tests "com.github.shekohex.whisperpp.data.SettingsBackupCryptoTest"` is blocked by pre-existing unrelated settings UI sources already in the working tree: `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsHelp.kt` has unresolved `rememberSaveable` references and `settings_help_action` resource usage. Left untouched per execution scope boundary.
