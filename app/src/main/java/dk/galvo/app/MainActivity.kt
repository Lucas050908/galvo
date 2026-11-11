package dk.galvo.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject

class MainActivity : Activity() {

    private lateinit var web: WebView
    private var pageReady = false
    private var pendingQuestion: String? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ttsDanish = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#0F1020")
        window.navigationBarColor = Color.parseColor("#1A1C33")
        web = WebView(this)
        web.setBackgroundColor(Color.parseColor("#0F1020"))
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.addJavascriptInterface(Bridge(), "Android")
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (url.startsWith("http")) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    return true
                }
                return false
            }
            override fun onPageFinished(view: WebView, url: String) {
                pageReady = true
                pendingQuestion?.let { openQuestion(it) }
                pendingQuestion = null
            }
        }
        setContentView(web)
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val t = tts ?: return@TextToSpeech
                val res = t.setLanguage(Locale("da", "DK"))
                ttsDanish = res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED
                if (!ttsDanish) t.language = Locale.getDefault()
                ttsReady = true
                t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) { done() }
                    @Deprecated("Deprecated in Java")
                    override fun onError(id: String?) { done() }
                })
            }
        }
        handleIntent(intent)
        web.loadUrl("file:///android_asset/index.html")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun done() {
        runOnUiThread { web.evaluateJavascript("window.galvoSpeakDone && window.galvoSpeakDone()", null) }
    }

    override fun onPause() {
        super.onPause()
        tts?.stop()
    }

    override fun onDestroy() {
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        if (pageReady) web.evaluateJavascript("window.galvoRefresh && window.galvoRefresh()", null)
    }

    private fun handleIntent(intent: Intent?) {
        val qid = intent?.getStringExtra(EXTRA_QUESTION) ?: return
        if (pageReady) openQuestion(qid) else pendingQuestion = qid
    }

    private fun openQuestion(qid: String) {
        val safe = qid.replace(Regex("[^a-zA-Z0-9_]"), "")
        web.evaluateJavascript("window.galvoQuestion && window.galvoQuestion('$safe')", null)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        web.evaluateJavascript("window.galvoBack ? window.galvoBack() : false") { r ->
            if (r != "true") super.onBackPressed()
        }
    }

    inner class Bridge {
        @JavascriptInterface
        fun share(text: String) {
            runOnUiThread {
                val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
                startActivity(Intent.createChooser(send, "Del"))
            }
        }

        /** Returns false when the phone's text-to-speech engine is not ready, so the page can tell the user. */
        @JavascriptInterface
        fun speak(text: String, rate: Double): Boolean {
            val t = tts ?: return false
            if (!ttsReady) return false
            t.setSpeechRate(rate.toFloat())
            return t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "galvo") == TextToSpeech.SUCCESS
        }

        /** "da" when a Danish voice is in use, "other" when the phone lacks one, "" while the engine is starting. */
        @JavascriptInterface
        fun ttsLang(): String = if (!ttsReady) "" else if (ttsDanish) "da" else "other"

        @JavascriptInterface
        fun copy(text: String) {
            runOnUiThread {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Galvo", text))
            }
        }

        @JavascriptInterface
        fun stopSpeak() { tts?.stop() }

        @JavascriptInterface
        fun saveState(json: String) = GalvoStore.saveState(this@MainActivity, json)

        @JavascriptInterface
        fun getState(): String = GalvoStore.state(this@MainActivity)

        @JavascriptInterface
        fun setCases(json: String) = GalvoStore.saveCases(this@MainActivity, json)

        @JavascriptInterface
        fun setQuestions(json: String) = GalvoStore.saveQuestions(this@MainActivity, json)

        @JavascriptInterface
        fun getReminder(): String = JSONObject()
            .put("time", GalvoStore.reminderTime(this@MainActivity))
            .put("on", GalvoStore.reminderOn(this@MainActivity))
            .toString()

        @JavascriptInterface
        fun setReminder(time: String, on: Boolean) {
            GalvoStore.setReminder(this@MainActivity, time, on)
            if (on) {
                runOnUiThread { askNotificationPermission() }
                ReminderScheduler.schedule(this@MainActivity)
            } else {
                ReminderScheduler.cancel(this@MainActivity)
            }
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    companion object {
        const val EXTRA_QUESTION = "galvo_question"
    }
}
