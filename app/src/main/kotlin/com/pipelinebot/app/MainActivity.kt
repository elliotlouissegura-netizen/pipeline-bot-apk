package com.pipelinebot.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var loadingView: View
    private lateinit var loadingMsg: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var retryCount = 0
    private val maxRetries = 120   // 60 secondes max (500ms chaque)
    private var dashboardLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView    = findViewById(R.id.webView)
        loadingView = findViewById(R.id.loadingView)
        loadingMsg  = findViewById(R.id.loadingMsg)

        setupWebView()
        startBot()
        handler.postDelayed(checkServerRunnable, 1500)
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled    = true
            domStorageEnabled    = true
            loadWithOverviewMode = true
            useWideViewPort      = true
            setSupportZoom(false)
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (url?.contains("localhost:5050") == true) {
                    loadingView.visibility = View.GONE
                    dashboardLoaded = true
                }
            }
            override fun onReceivedError(
                view: WebView?, request: WebResourceRequest?, error: WebResourceError?
            ) {
                // Flask pas encore prêt — le polling va relancer
                if (!dashboardLoaded) {
                    webView.stopLoading()
                }
            }
        }
    }

    private fun startBot() {
        // Lance start_silent.sh via l'API RUN_COMMAND de Termux
        // Nécessite : allow-external-apps = true dans ~/.termux/termux.properties
        try {
            val intent = Intent().apply {
                setClassName("com.termux", "com.termux.app.RunCommandService")
                action = "com.termux.RUN_COMMAND"
                putExtra(
                    "com.termux.RUN_COMMAND_PATH",
                    "/data/data/com.termux/files/usr/bin/bash"
                )
                putExtra(
                    "com.termux.RUN_COMMAND_ARGUMENTS",
                    arrayOf("/data/data/com.termux/files/home/start_silent.sh")
                )
                putExtra(
                    "com.termux.RUN_COMMAND_WORKDIR",
                    "/data/data/com.termux/files/home"
                )
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
                putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0")
            }
            startService(intent)
        } catch (e: Exception) {
            updateMsg("Vérifie que Termux est installé")
        }
    }

    private val checkServerRunnable = object : Runnable {
        override fun run() {
            if (dashboardLoaded) return
            Thread {
                val isUp = try {
                    val conn = URL("http://localhost:5050").openConnection() as HttpURLConnection
                    conn.connectTimeout = 400
                    conn.readTimeout    = 400
                    conn.requestMethod  = "GET"
                    conn.connect()
                    val code = conn.responseCode
                    conn.disconnect()
                    code == 200
                } catch (e: Exception) {
                    false
                }
                handler.post {
                    if (isUp) {
                        webView.loadUrl("http://localhost:5050")
                    } else {
                        retryCount++
                        when {
                            retryCount < 10 -> updateMsg("Démarrage du bot...")
                            retryCount < 40 -> updateMsg("En cours (${retryCount / 2}s)...")
                            retryCount < maxRetries -> updateMsg("Toujours en cours (${retryCount / 2}s)...")
                            else -> {
                                updateMsg("Bot non démarré.\nVérifie Termux + allow-external-apps.")
                                return@post
                            }
                        }
                        handler.postDelayed(this, 500)
                    }
                }
            }.start()
        }
    }

    private fun updateMsg(text: String) {
        loadingMsg.text = text
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
