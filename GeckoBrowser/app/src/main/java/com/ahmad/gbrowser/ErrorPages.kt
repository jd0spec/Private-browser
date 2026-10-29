package com.ahmad.gbrowser

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mozilla.geckoview.WebRequestError

/** The package name of the VPN app, shared between the settings screen and the error pages. */
object VpnSetting {
    var pkg by mutableStateOf("")
}

object ErrorPages {
    // Looked up by name so a missing constant can never break the build.
    private fun code(name: String): Int = try {
        WebRequestError::class.java.getField(name).getInt(null)
    } catch (e: Exception) {
        Int.MIN_VALUE
    }

    private fun esc(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    fun page(uri: String?, error: WebRequestError, vpnLabel: String): String {
        val c = error.code
        val (title, text) = when (c) {
            code("ERROR_OFFLINE") ->
                Pair("No internet connection", "Check your Wi-Fi or mobile data, then try again.")
            code("ERROR_UNKNOWN_HOST") ->
                Pair("Can't find this site", "The address may be wrong, or the site may be unavailable or blocked on your network.")
            code("ERROR_CONNECTION_REFUSED") ->
                Pair("Connection refused", "The site did not accept the connection. It may be down, or blocked on your network.")
            code("ERROR_NET_TIMEOUT") ->
                Pair("Connection timed out", "The site took too long to answer. It may be down, or blocked on your network.")
            code("ERROR_NET_RESET"), code("ERROR_NET_INTERRUPT") ->
                Pair("Connection was interrupted", "The connection was cut while loading. This can happen when a site is blocked on your network.")
            code("ERROR_SECURITY_BAD_CERT"), code("ERROR_SECURITY_SSL"), code("ERROR_BAD_HSTS_CERT") ->
                Pair("Connection isn't secure", "The secure connection to this site could not be verified.")
            code("ERROR_MALFORMED_URI") ->
                Pair("Invalid address", "This address is not written correctly.")
            code("ERROR_REDIRECT_LOOP") ->
                Pair("Too many redirects", "The site keeps sending you in circles.")
            code("ERROR_UNKNOWN_PROTOCOL") ->
                Pair("Can't open this link", "This kind of link is not supported.")
            code("ERROR_PORT_BLOCKED") ->
                Pair("Port is blocked", "The browser does not allow connections to this port.")
            else ->
                Pair("This page can't load", "Something went wrong while loading the page.")
        }
        val shown = uri ?: ""
        val host = try {
            Uri.parse(shown).host ?: shown
        } catch (e: Exception) {
            shown
        }
        val enc = Uri.encode(shown)

        val html = """
<!doctype html>
<html><head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${esc(title)}</title>
<style>
:root{--bg:#fef7ff;--fg:#1d1b20;--sub:#49454f;--pri:#6750a4;--onpri:#ffffff;--cont:#eaddff;--oncont:#21005d;--chip:#f3edf7;--line:#cac4d0}
@media (prefers-color-scheme: dark){
:root{--bg:#141218;--fg:#e6e0e9;--sub:#cac4d0;--pri:#d0bcff;--onpri:#381e72;--cont:#4f378b;--oncont:#eaddff;--chip:#211f26;--line:#49454f}
}
*{box-sizing:border-box}
html,body{margin:0;background:var(--bg);color:var(--fg);font-family:sans-serif}
body{min-height:100vh;display:flex;align-items:center;justify-content:center;padding:24px}
.card{width:100%;max-width:420px;text-align:center}
.icon{width:84px;height:84px;border-radius:28px;background:var(--cont);color:var(--oncont);display:flex;align-items:center;justify-content:center;margin:0 auto 24px}
h1{font-size:24px;font-weight:600;margin:0 0 12px}
p{font-size:15px;line-height:1.5;color:var(--sub);margin:0 0 16px}
.host{display:inline-block;max-width:100%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;background:var(--chip);color:var(--fg);border-radius:14px;padding:8px 16px;font-size:14px;margin-bottom:28px}
.btn{display:block;width:100%;padding:16px;border-radius:28px;font-size:16px;font-weight:600;text-decoration:none;margin-bottom:12px}
.main{background:var(--pri);color:var(--onpri)}
.tonal{background:var(--cont);color:var(--oncont)}
.note{font-size:13px;color:var(--sub);margin-top:20px}
.code{font-size:12px;color:var(--sub);opacity:.7;margin-top:8px}
</style></head>
<body><div class="card">
<div class="icon"><svg width="40" height="40" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z"/></svg></div>
<h1>${esc(title)}</h1>
<p>${esc(text)}</p>
<div class="host">${esc(host)}</div>
<a class="btn main" href="gbrowser://retry?u=$enc">Retry</a>
<a class="btn tonal" href="gbrowser://vpn?u=$enc">${esc(vpnLabel)}</a>
<div class="note">If this site is blocked on your network, connecting a VPN may help. Come back here afterwards and the page reloads by itself.</div>
<div class="code">Error code: $c</div>
</div></body></html>
"""
        return "data:text/html;charset=utf-8," + Uri.encode(html)
    }
}

@Composable
fun VpnAppDialog(
    current: String,
    resolve: (String) -> String?,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(current) }
    val found = remember(text) { if (text.isBlank()) null else resolve(text.trim()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("VPN app") },
        text = {
            Column {
                Text(
                    "Enter the package name of your VPN app. The VPN button on error pages will open it.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    singleLine = true, label = { Text("Package name") }
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        text.isBlank() -> "Examples: ch.protonvpn.android, com.windscribe.vpn, com.cloudflare.onedotonedotonedotone"
                        found != null -> "Found: $found"
                        else -> "No installed app has this package name"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text.trim()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
