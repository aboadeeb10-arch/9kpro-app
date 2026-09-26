package tv.ninekpro.app

/** Set by MainActivity when opened from a reminder / TV row / widget: Sports tab, a live channel, or a movie to resume. */
object Launch { @Volatile var openSports = false; @Volatile var openChannel: String = ""; @Volatile var openResume: String = "" }
