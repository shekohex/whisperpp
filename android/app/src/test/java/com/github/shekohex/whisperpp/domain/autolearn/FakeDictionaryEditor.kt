package com.github.shekohex.whisperpp.domain.autolearn

import com.github.shekohex.whisperpp.data.autolearn.AutoLearnDictionaryEditor
import com.github.shekohex.whisperpp.data.dictionary.DictionaryBackup

class FakeDictionaryEditor(var dictionary: DictionaryBackup = DictionaryBackup()) : AutoLearnDictionaryEditor {
    override suspend fun edit(transform: (DictionaryBackup) -> DictionaryBackup) {
        dictionary = transform(dictionary)
    }
}
