package lt.bettertamo.ui

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.view.ContextThemeWrapper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

class WebHolder {
    var view: WebView? = null
    var loaded: String? = null
    var dark: Boolean? = null
    val progress = mutableIntStateOf(0)
    val failed = mutableStateOf(false)
    val canGoBack = mutableStateOf(false)
    val generation = mutableIntStateOf(0)
    var chooser: ((ValueCallback<Array<Uri>>, Intent) -> Boolean)? = null
    var close: (() -> Unit)? = null
    var then: String? = null
    var armed = false
    var leave: (() -> Unit)? = null

    fun back() {
        val view = view ?: return
        if (canGoBack.value) view.goBack()
    }

    fun destroy() {
        view?.let { (it.parent as? ViewGroup)?.removeView(it); it.destroy() }
        view = null; loaded = null; canGoBack.value = false
        generation.intValue++
    }
}

private fun tamoHost(uri: Uri) = uri.scheme == "https" && uri.host?.let { it == "tamo.lt" || it.endsWith(".tamo.lt") } == true

private fun WebHolder.navigated(view: WebView, url: String?) {
    val uri = (url ?: return).toUri()
    if (uri.host != "bendrauk.tamo.lt") return
    val path = uri.path.orEmpty()
    val composing = "/Messages/New" in path || "/Messages/Reply" in path
    then?.let { target ->
        then = null
        if (!composing) view.loadUrl(target)
    }
    if (composing) armed = true
    else if (armed && (path == "/" || path.endsWith("/Messages/Received") || path.endsWith("/Messages/Sent"))) {
        armed = false
        leave?.invoke()
    }
}

private fun WebHolder.update(view: WebView) {
    val list = view.copyBackForwardList()
    val previous = if (list.currentIndex > 0) list.getItemAtIndex(list.currentIndex - 1)?.url.orEmpty() else ""
    canGoBack.value = view.canGoBack() && "NavigateDirect" !in previous
}

@SuppressLint("SetJavaScriptEnabled")
private fun createWebView(context: Context, dark: Boolean, holder: WebHolder): WebView {
    val themed = ContextThemeWrapper(context, if (dark) android.R.style.Theme_Material_NoActionBar else android.R.style.Theme_Material_Light_NoActionBar)
    return WebView(themed).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.setSupportMultipleWindows(false)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, true)
        CookieManager.getInstance().setAcceptCookie(true)
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                if (uri.path.orEmpty().contains("NavigateClose", ignoreCase = true)) { holder.close?.invoke(); return true }
                if (tamoHost(uri)) return false
                if (uri.scheme in listOf("http", "https", "mailto", "tel")) runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                return true
            }
            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) { holder.failed.value = false }
            override fun onPageFinished(view: WebView, url: String?) { holder.update(view); holder.navigated(view, url) }
            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) { holder.update(view); holder.navigated(view, url) }
            override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
                if (holder.view === view) { holder.destroy(); holder.failed.value = true }
                return true
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) holder.failed.value = true
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) { holder.progress.intValue = newProgress }
            override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean =
                holder.chooser?.invoke(callback, params.createIntent()) ?: false
        }
        setDownloadListener { url, userAgent, disposition, mime, _ ->
            val uri = url.toUri()
            if (uri.scheme != "https") return@setDownloadListener
            runCatching {
                val name = URLUtil.guessFileName(url, disposition, mime)
                val request = DownloadManager.Request(uri).setMimeType(mime).addRequestHeader("User-Agent", userAgent)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name).setTitle(name)
                if (tamoHost(uri)) CookieManager.getInstance().getCookie(url)?.let { request.addRequestHeader("Cookie", it) }
                (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            }.onFailure { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) } }
        }
    }
}

@Composable
fun TamoWebContent(holder: WebHolder, target: String, modifier: Modifier = Modifier, auth: Boolean = true, then: String? = null, onLeave: (() -> Unit)? = null, onClose: () -> Unit = {}) {
    val vm = LocalPlanner.current
    val context = LocalContext.current
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    var url by remember(target) { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        pending?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
        pending = null
    }
    DisposableEffect(holder) {
        holder.chooser = { callback, intent ->
            pending?.onReceiveValue(null)
            pending = callback
            runCatching { files.launch(intent) }.isSuccess
        }
        holder.close = onClose
        holder.leave = onLeave
        if (holder.loaded == null) holder.then = then
        onDispose { holder.chooser = null; holder.close = null; holder.leave = null }
    }
    LaunchedEffect(dark) { if (holder.dark != null && holder.dark != dark) holder.destroy() }
    LaunchedEffect(target, holder.generation.intValue) {
        if (holder.view == null || holder.loaded != target) url = if (auth) vm.webUrl(target) else target.takeIf { it.startsWith("https://") }
    }
    BackHandler(holder.canGoBack.value) { holder.back() }
    Box(modifier.fillMaxSize()) {
        key(dark, holder.generation.intValue) { AndroidView(
            factory = { ctx ->
                holder.view?.also { (it.parent as? ViewGroup)?.removeView(it) } ?: createWebView(ctx, dark, holder).also { holder.view = it; holder.dark = dark }
            },
            update = { view ->
                val next = url
                if (next != null && holder.loaded != target) {
                    holder.loaded = target
                    view.loadUrl(next)
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) }
        if (holder.progress.intValue in 0..99) LinearProgressIndicator(progress = { holder.progress.intValue / 100f }, modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter), drawStopIndicator = {})
        if (holder.failed.value) Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
                Icon(Icons.Outlined.CloudOff, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Nepavyko įkelti", style = MaterialTheme.typography.titleLarge)
                Text("Patikrinkite interneto ryšį ir bandykite dar kartą.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { reloadWeb(holder) }) {
                    Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Bandyti dar kartą")
                }
            }
        }
    }
}

fun reloadWeb(holder: WebHolder) {
    holder.failed.value = false
    holder.view?.reload() ?: run { holder.generation.intValue++ }
}
