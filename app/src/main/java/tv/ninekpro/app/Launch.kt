package tv.ninekpro.app

import kotlinx.coroutines.flow.MutableStateFlow

/** Set by MainActivity when opened from a reminder / TV row / widget: Sports tab, a live channel, or a movie to resume. */
object Launch {
    // old flags, still honoured by App.kt until it moves to `pending`
    @Volatile var openSports = false; @Volatile var openChannel: String = ""; @Volatile var openResume: String = ""

    /** kind = "channel" (key = channel key), "resume" (key = resume key) or "sports". */
    data class Open(val kind: String, val key: String = "")
    /** MainActivity sets it in onCreate / onNewIntent, App.kt collects it and opens the screen. */
    val pending = MutableStateFlow<Open?>(null)
    /** Returns the pending request and clears it (null when there is none). */
    fun take(): Open? { val v = pending.value; if (v != null) pending.value = null; return v }
}
