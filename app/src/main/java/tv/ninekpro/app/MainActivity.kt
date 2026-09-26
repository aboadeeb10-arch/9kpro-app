package tv.ninekpro.app

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tv.ninekpro.app.data.Prefs
import tv.ninekpro.app.ui.App
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        val lang = try { Prefs(newBase).language } catch (e: Throwable) { "" }
        if (lang.isEmpty()) { super.attachBaseContext(newBase); return }
        val loc = Locale(lang); Locale.setDefault(loc)
        val cfg = Configuration(newBase.resources.configuration); cfg.setLocale(loc); cfg.setLayoutDirection(loc)
        super.attachBaseContext(newBase.createConfigurationContext(cfg))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as SmileApp
        val blocking = app.initError?.let { "SmileApp.onCreate\n" + (Crash.lastTrace(this) ?: it) } ?: Crash.blocking(this)
        val repo = app.repo
        if (blocking != null || repo == null) {
            setContent { CrashScreen(blocking ?: "The app could not start (no repo).") }
            return
        }
        immersive()
        Crash.onAppStarted(this)
        if (intent?.hasExtra("match") == true) Launch.openSports = true
        intent?.getStringExtra("chkey")?.let { if (it.isNotEmpty()) Launch.openChannel = it }
        intent?.getStringExtra("resume_key")?.let { if (it.isNotEmpty()) Launch.openResume = it }
        setContent { App(repo, this) }
    }
    override fun onNewIntent(intent: android.content.Intent) { super.onNewIntent(intent); if (intent.hasExtra("match")) Launch.openSports = true; intent.getStringExtra("chkey")?.let { if (it.isNotEmpty()) Launch.openChannel = it }; intent.getStringExtra("resume_key")?.let { if (it.isNotEmpty()) Launch.openResume = it } }

    /** Receiver-style full screen: Android's status + navigation bars stay hidden everywhere; a swipe from the edge shows them briefly. */
    private fun immersive() {
        try {
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
            val ic = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
            ic.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ic.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        } catch (e: Throwable) {}
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (hasFocus) immersive() }
    override fun onResume() { super.onResume(); immersive() }
    override fun onDestroy() { super.onDestroy(); if (isFinishing) tv.ninekpro.app.ui.LiveEngine.release() }

    /** Shown instead of the app after two crashes in a row: plain Compose, no theme, no fonts — nothing that could itself crash. */
    @Composable
    private fun CrashScreen(trace: String) {
        Column(Modifier.fillMaxSize().background(Color(0xFF000000)).padding(20.dp)) {
            Text("9K Pro TV could not start", color = Color.White, fontSize = 22.sp)
            Text("This report was sent to your provider. Version " + BuildConfig.VERSION_NAME, color = Color(0xFF9FB3C8), fontSize = 13.sp)
            Text(trace, color = Color(0xFFFFD166), fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { Thread { val ok = Crash.send(trace); runOnUiThread { Toast.makeText(this@MainActivity, if (ok) "Report sent" else "Could not send — check internet", Toast.LENGTH_LONG).show() } }.start() }) { Text("Send report") }
                Button(onClick = { Crash.reset(this@MainActivity); recreate() }) { Text("Try again") }
                Button(onClick = { try { getSharedPreferences("ninekpro", Context.MODE_PRIVATE).edit().clear().commit() } catch (e: Throwable) {}; Crash.reset(this@MainActivity); Toast.makeText(this@MainActivity, "Data cleared — open 9K Pro TV again", Toast.LENGTH_LONG).show(); finishAffinity(); android.os.Process.killProcess(android.os.Process.myPid()) }) { Text("Reset app data") }
            }
        }
    }
}
