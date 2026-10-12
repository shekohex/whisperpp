package com.github.shekohex.whisperpp.providers

import com.github.liuyueyi.quick.transfer.ChineseUtils
import com.github.liuyueyi.quick.transfer.constants.TransType
import com.github.shekohex.whisperpp.domain.providers.ScriptConversion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** Chinese simplified/traditional post-processing (A-01); dictionaries load lazily off the main thread. */
object ChineseScriptConverter {
    private val loadedDictionaries: MutableSet<TransType> = ConcurrentHashMap.newKeySet()

    suspend fun convert(text: String, conversion: ScriptConversion): String = when (conversion) {
        ScriptConversion.NONE -> text
        ScriptConversion.TO_SIMPLIFIED -> {
            ensureLoaded(TransType.TAIWAN_TO_SIMPLE)
            ChineseUtils.tw2s(text)
        }
        ScriptConversion.TO_TRADITIONAL -> {
            ensureLoaded(TransType.SIMPLE_TO_TAIWAN)
            ChineseUtils.s2tw(text)
        }
    }

    private suspend fun ensureLoaded(type: TransType) {
        if (!loadedDictionaries.add(type)) return
        withContext(Dispatchers.IO) { ChineseUtils.preLoad(false, type) }
    }
}
