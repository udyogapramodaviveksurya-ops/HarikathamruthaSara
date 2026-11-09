package com.example.harikathamruthasara.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.harikathamruthasara.data.Script
import com.example.harikathamruthasara.i18n.strings

const val HKS_SCRIPT_URL = "https://sites.google.com/site/madhwaprameyaqa/hks-script-audio"
const val SILENT_PAUSE_URL = "https://soundcloud.com/shriharivayu-gurugalu/silent-pause-usage-of-dash-in-hks-script"

@Composable
fun CreditsScreen(nav: Nav) {
    val S = strings()
    val ctx = LocalContext.current
    fun open(url: String) = runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

    Scaffold(topBar = { BackBar(S.creditsTitle, nav) }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).readable().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandLogo(120.dp)
            // dedication first
            CreditCard("🙏 " + S.creditPranams, S.creditPranamsBody, tone = tones.marigold)
            ShlokaCard()
            CreditCard("📜 " + S.creditWork, S.creditWorkBody, tone = tones.marigold)
            CreditCard("📖 " + S.creditText, "Tadipatri Gurukula\nShri Kesava Rao Tadipatri", S.creditTextBody, tone = tones.lotus) {
                OutlinedButton(onClick = { open(HKS_SCRIPT_URL) }) { Text(S.openWebsite + " ↗") }
                OutlinedButton(onClick = { open(SILENT_PAUSE_URL) }) { Text("🔇 Silent pause (dash) ↗") }
            }
            CreditCard("🎶 " + S.creditVag, "Vāgdhenu · Dr. Prathosh Acharya (Prathosh A P)\nProfessor, Indian Institute of Science (IISc), Bengaluru\nCreator of Vāgdhenu", S.creditVagBody, tone = tones.peacock) {
                OutlinedButton(onClick = { open("https://prathosh.in/vagdhenu/") }) { Text("prathosh.in/vagdhenu ↗") }
            }
            CreditCard("🎧 " + S.creditEar, "Vakyansh · EkStep Foundation\nwav2vec2 Kannada (MIT License)", S.creditEarBody, tone = tones.sky) {
                OutlinedButton(onClick = { open("https://github.com/Open-Speech-EkStep/vakyansh-models") }) { Text("Vakyansh ↗") }
            }
            CreditCard("❤️ " + S.creditMadeBy, "S. Vivek Surya  &  Janaki", S.creditMadeByRole, tone = tones.berry)
            Text(S.personalUse, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Offering()
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Ayodhyā Mathurā Māyā …: the seven cities that grant mokṣa — Kāñcī among them. */
private const val SHLOKA_KN = "ಅಯೋಧ್ಯಾ ಮಥುರಾ ಮಾಯಾ ಕಾಶೀ ಕಾಂಚೀ ಅವಂತಿಕಾ\u00A0।\nಪುರೀ ದ್ವಾರಾವತೀ ಚೈವ ಸಪ್ತೈತಾ ಮೋಕ್ಷದಾಯಿಕಾಃ\u00A0॥"
private const val SHLOKA_DEVA = "अयोध्या मथुरा माया काशी काञ्ची अवन्तिका\u00A0।\nपुरी द्वारावती चैव सप्तैता मोक्षदायिकाः\u00A0॥"

@Composable
private fun ShlokaCard() {
    val S = strings()
    val tone = tones.lotus
    androidx.compose.material3.Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = androidx.compose.foundation.BorderStroke(2.dp, tone.accent.copy(alpha = 0.6f))) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("🪷 " + S.creditShloka, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
            Text(SHLOKA_DEVA, fontSize = 20.sp, lineHeight = 32.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
            Text(SHLOKA_KN, fontSize = 18.sp, lineHeight = 29.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val own = S.lang.script
            if (own != Script.KANNADA && own != Script.DEVANAGARI)
                Text(com.example.harikathamruthasara.data.Scripts.render(SHLOKA_KN, own), fontSize = 16.sp, lineHeight = 26.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.tertiary)
        }
    }
}

@Composable
private fun CreditCard(title: String, name: String, body: String? = null, tone: Tone, extra: @Composable () -> Unit = {}) {
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = tone.container, contentColor = tone.on)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            if (body == null) Text(name, fontSize = 15.sp) else {
                Text(name, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(body, fontSize = 14.sp)
            }
            extra()
        }
    }
}

/** ಶ್ರೀಕೃಷ್ಣಾರ್ಪಣಮಸ್ತು, in Kannada and the reader's own script. */
@Composable
fun Offering(modifier: Modifier = Modifier, size: Int = 22) {
    val S = strings()
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🙏", fontSize = (size + 6).sp)
        Text(S.krishnarpanam, fontSize = size.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        if (S.lang.script != Script.KANNADA) Text(mine(S.krishnarpanam), fontSize = (size - 6).sp, color = MaterialTheme.colorScheme.secondary)
    }
}
