package com.enzi.music

import android.app.Activity
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Google sign-in for Android. The desktop app opens a second window for this; an Android app gets
 * one webview, so sign-in gets a screen of its own with its own browser view instead. Once Google
 * sends it on to music.youtube.com, the YouTube cookies are the session: they go to Rust through
 * [LoginBridge], which signs in exactly as the desktop login window does.
 */
class LoginActivity : Activity() {
    companion object {
        const val EXTRA_ADD_ACCOUNT = "add_account"
        private const val LOGIN_URL =
            "https://accounts.google.com/ServiceLogin?service=youtube&continue=https://music.youtube.com/"
        private const val ADD_ACCOUNT_URL =
            "https://accounts.google.com/AddSession?service=youtube&continue=https://music.youtube.com/"
    }

    private lateinit var web: WebView
    private var done = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cookies = CookieManager.getInstance()
        cookies.setAcceptCookie(true)
        web = WebView(this)
        cookies.setAcceptThirdPartyCookies(web, true)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        // Google refuses sign-in in anything that calls itself an embedded webview ("wv").
        web.settings.userAgentString = web.settings.userAgentString.replace("; wv", "")
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                if (Uri.parse(url).host == "music.youtube.com") capture(0)
            }
        }
        web.setBackgroundColor(Color.rgb(10, 10, 10))
        setContentView(web)
        // Android 15 and up draw under the system bars; keep the page clear of them and the keyboard.
        ViewCompat.setOnApplyWindowInsetsListener(web) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, keyboard.bottom))
            insets
        }

        if (intent.getBooleanExtra(EXTRA_ADD_ACCOUNT, false)) {
            // Forget this view's Google session, so YouTube Music lands on the account entered
            // next rather than continuing as the one it already had. Saved accounts live in Rust.
            cookies.removeAllCookies { web.loadUrl(ADD_ACCOUNT_URL) }
        } else {
            web.loadUrl(LOGIN_URL)
        }
    }

    /** The redirect sets the YouTube cookies, sometimes a beat after the page finishes loading. */
    private fun capture(attempt: Int) {
        if (done) return
        val cookie = CookieManager.getInstance().getCookie("https://music.youtube.com") ?: ""
        if (cookie.contains("SAPISID=")) {
            done = true
            try {
                LoginBridge.onCookies(cookie)
            } catch (e: Throwable) {
                android.util.Log.w("EnziMusic", "sign-in didn't reach Rust", e)
            }
            finish()
            return
        }
        // Landed on YouTube Music without a session yet: keep watching.
        if (attempt < 6) web.postDelayed({ capture(attempt + 1) }, 500)
    }

    @Deprecated("Still the way to intercept Back on the API levels this app supports.")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }
}
