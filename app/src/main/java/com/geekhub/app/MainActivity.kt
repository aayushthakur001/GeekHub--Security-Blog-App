package com.geekhub.app

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.geekhub.app.ui.theme.GeekHubTheme
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class SplashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Set content for Splash screen
        setContent {
            GeekHubTheme {
                SplashScreen()
            }
        }

        // Delay to transition to MainActivity
        Handler().postDelayed({
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish() // Finish SplashActivity so the user can't navigate back to it
        }, 3000) // 3 seconds delay for splash
    }
}


@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "Welcome to GeekHub!")
    }
}


class MainActivity : ComponentActivity() {

    private val reloadTrigger = MutableStateFlow(0)
    private val showSnackbar = MutableStateFlow(false)

    private lateinit var networkChangeReceiver: BroadcastReceiver
    private var lastBackPressedTime: Long = 0
    private lateinit var webViewRef: WebView // Updated to lateinit

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MobileAds.initialize(this)

        // Network reconnect receiver
        networkChangeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (context != null && isNetworkAvailable(context)) {
                    showSnackbar.value = true
                    reloadTrigger.value += 1
                }
            }
        }
        registerReceiver(
            networkChangeReceiver,
            IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        )

        // Handle back press with WebView and double-exit
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                webViewRef.let { webView ->
                    if (webView.canGoBack()) {
                        webView.goBack()
                    } else {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastBackPressedTime < 2000) {
                            finish()
                        } else {
                            lastBackPressedTime = currentTime
                            Toast.makeText(
                                this@MainActivity,
                                "Press again to exit",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        })

        setContent {
            val context = LocalContext.current
            val trigger by reloadTrigger.collectAsState()
            val snackbarHostState = remember { SnackbarHostState() }
            val coroutineScope = rememberCoroutineScope()
            val showMessage by showSnackbar.collectAsState()

            LaunchedEffect(showMessage) {
                if (showMessage) {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Internet restored. Reloading content...")
                        showSnackbar.value = false
                    }
                }
            }

            GeekHubTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        WebPage(
                            modifier = Modifier.weight(1f),
                            reloadKey = trigger,
                            onManualRetry = {
                                reloadTrigger.value += 1
                            }
                        )
                        AdMobBanner()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(networkChangeReceiver)
    }

    // Updated WebPage function to include the provided implementation
    @Composable
    fun WebPage(modifier: Modifier = Modifier) {
        val context = LocalContext.current

        Column(modifier = modifier.fillMaxSize()) {
            AndroidView(
                factory = {
                    WebView(context).apply {
                        webViewClient = WebViewClient()
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.setSupportZoom(true)
                        settings.cacheMode = WebSettings.LOAD_NO_CACHE

                        // Force Dark Mode for Android 10 and above
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                                WebSettingsCompat.setForceDark(settings, WebSettingsCompat.FORCE_DARK_ON)
                            }
                        }

                        // Load your website URL
                        loadUrl("https://geekhub01.blogspot.com/?m=0")
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun WebPage(modifier: Modifier = Modifier, reloadKey: Int, onManualRetry: () -> Unit) {
    val context = LocalContext.current
    var isOffline by remember { mutableStateOf(!isNetworkAvailable(context)) }
    var showGame by remember { mutableStateOf(false) }
    val currentReloadKey by rememberUpdatedState(reloadKey)

    Column(modifier = modifier.fillMaxSize()) {
        var webView: WebView? = remember { null }
        val shouldShowGame by rememberUpdatedState(showGame)

        AndroidView(
            factory = {
                WebView(context).apply {
                    webViewClient = WebViewClient()
                    settings.javaScriptEnabled = true

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = true
                        allowContentAccess = true
                        allowFileAccessFromFileURLs = true
                        allowUniversalAccessFromFileURLs = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        setSupportZoom(false)
                        builtInZoomControls = false
                        displayZoomControls = false
                        cacheMode = WebSettings.LOAD_DEFAULT
                        setRenderPriority(WebSettings.RenderPriority.HIGH) // Helps prioritize rendering (deprecated in API 18+ but still useful for some devices)
                    }

                    // ✅ Handle downloads
                    setDownloadListener { url, _, _, _, _ ->
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    }

                    var webViewRef = this // Set reference for back press handling
                    webView = this

                    // ✅ Initial load
                    if (isOffline) {
                        if (shouldShowGame) {
                            loadUrl("file:///android_asset/surf_game/index.html")
                        } else {
                            loadUrl("file:///android_asset/offline.html")
                        }
                    } else {
                        loadUrl("https://geekhub01.blogspot.com/")
                    }
                }
            },
            update = {
                isOffline = !isNetworkAvailable(context)

                if (isOffline) {
                    if (shouldShowGame) {
                        it.loadUrl("file:///android_asset/surf_game/index.html")
                    } else {
                        it.loadUrl("file:///android_asset/offline.html")
                    }
                } else {
                    it.loadUrl("https://geekhub01.blogspot.com/")
                    showGame = false
                }
            },
            modifier = Modifier.weight(1f)
        )

        if (isOffline && !showGame) {
            Button(
                onClick = onManualRetry,
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth()
            ) {
                Text("Retry")
            }

            Button(
                onClick = {
                    showGame = true
                    webView?.loadUrl("file:///android_asset/surf_game/index.html") // 🔥 Force load the game now
                },
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth()
            ) {
                Text("Play Game")
            }
        }
    }
}


@Composable
fun AdMobBanner() {
    val context = LocalContext.current
    AndroidView(
        factory = {
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = "ca-app-pub-3940256099942544/6300978111"
                loadAd(AdRequest.Builder().build())
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
    )
}

// ✅ Network checker
fun isNetworkAvailable(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        activeNetwork.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } else {
        @Suppress("DEPRECATION")
        val networkInfo = connectivityManager.activeNetworkInfo
        @Suppress("DEPRECATION")
        networkInfo != null && networkInfo.isConnected
    }
}
