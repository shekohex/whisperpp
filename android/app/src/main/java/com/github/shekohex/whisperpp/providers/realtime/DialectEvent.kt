package com.github.shekohex.whisperpp.providers.realtime

import com.github.shekohex.whisperpp.core.network.NetworkError

/** What a [StreamingDialect] makes of one incoming message. */
internal sealed interface DialectEvent {
    /** The server accepted the session; audio may flow. */
    data object Ready : DialectEvent

    /** Cumulative transcript so far (committed segments plus the one in progress). */
    class Partial(val text: String) : DialectEvent

    /** The server finished the session after the commit; [text] is the whole transcript. */
    class Done(val text: String) : DialectEvent

    class Failed(val error: NetworkError) : DialectEvent
}
