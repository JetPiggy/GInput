package com.jetpiggy.ginput.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jetpiggy.ginput.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 開源授權頁的一項。license 是 null 表示公有領域；notice 是 assets 裏授權全文的路徑。
 * 加入會打包進 App 的程式庫或資料時，要在這裏加一項（AGENTS.md）。
 */
internal data class LicenseEntry(
    val name: String,
    val license: String?,
    @StringRes val usage: Int,
    val url: String,
    val notice: String? = null,
)

private const val APACHE_2 = "Apache License 2.0"

internal val Licenses = listOf(
    LicenseEntry("words.hk", null, R.string.license_usage_wordshk, "https://words.hk/faiman/analysis/"),
    LicenseEntry("libcangjie", null, R.string.license_usage_libcangjie, "https://gitlab.freedesktop.org/cangjie/libcangjie"),
    LicenseEntry(
        "rime-cantonese",
        "CC BY 4.0",
        R.string.license_usage_rime_cantonese,
        "https://github.com/rime/rime-cantonese",
        notice = "licenses/rime-cantonese.txt",
    ),
    LicenseEntry(
        "12dicts",
        null,
        R.string.license_usage_12dicts,
        "http://wordlist.aspell.net/12dicts/",
        notice = "licenses/12dicts-agid.txt",
    ),
    LicenseEntry(
        "wordfreq",
        "CC BY-SA 4.0",
        R.string.license_usage_wordfreq,
        "https://github.com/rspeer/wordfreq",
        notice = "licenses/wordfreq.txt",
    ),
    LicenseEntry(
        "Unicode Emoji",
        "Unicode License v3",
        R.string.license_usage_unicode,
        "https://www.unicode.org/emoji/",
        notice = "licenses/unicode.txt",
    ),
    LicenseEntry(
        "Unicode CLDR",
        "Unicode License v3",
        R.string.license_usage_cldr,
        "https://cldr.unicode.org/",
        notice = "licenses/unicode.txt",
    ),
    LicenseEntry(
        "Material Symbols",
        APACHE_2,
        R.string.license_usage_material_symbols,
        "https://fonts.google.com/icons",
        notice = "licenses/apache-2.0.txt",
    ),
    LicenseEntry(
        "AndroidX / Jetpack Compose",
        APACHE_2,
        R.string.license_usage_androidx,
        "https://developer.android.com/jetpack/androidx",
        notice = "licenses/apache-2.0.txt",
    ),
    LicenseEntry(
        "Sentry Java SDK",
        "MIT",
        R.string.license_usage_sentry,
        "https://github.com/getsentry/sentry-java",
        notice = "licenses/sentry-java.txt",
    ),
    LicenseEntry(
        "epitaph",
        "MIT",
        R.string.license_usage_epitaph,
        "https://github.com/abovevacant/epitaph",
        notice = "licenses/epitaph.txt",
    ),
    LicenseEntry(
        "Kotlin / kotlinx.coroutines",
        APACHE_2,
        R.string.license_usage_kotlin,
        "https://kotlinlang.org/",
        notice = "licenses/apache-2.0.txt",
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LicensesScreen(onBack: () -> Unit, onOpenNotice: (Int) -> Unit) {
    val uriHandler = LocalUriHandler.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.licenses)) },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            Licenses.forEachIndexed { index, entry ->
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(entry.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = entry.license ?: stringResource(R.string.license_public_domain),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(stringResource(entry.usage), style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { uriHandler.openUri(entry.url) }) {
                            Text(stringResource(R.string.license_website))
                        }
                        if (entry.notice != null) {
                            TextButton(onClick = { onOpenNotice(index) }) {
                                Text(stringResource(R.string.license_notice))
                            }
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

/** 授權全文，原文照錄。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NoticeScreen(entry: LicenseEntry, onBack: () -> Unit) {
    val context = LocalContext.current
    val text by produceState("", entry.notice) {
        val path = entry.notice ?: return@produceState
        value = withContext(Dispatchers.IO) {
            context.assets.open(path).bufferedReader().use { it.readText() }
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(entry.name) },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        SelectionContainer(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = text,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            )
        }
    }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
    }
}
