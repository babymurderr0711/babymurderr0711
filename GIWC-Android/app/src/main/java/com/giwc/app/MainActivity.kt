package com.giwc.app

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

private val Bg = Color(0xFF090A10)
private val Panel = Color(0xFF171821)
private val Panel2 = Color(0xFF211B32)
private val Purple = Color(0xFFAE91E8)
private val Gold = Color(0xFFD9AE65)
private val Blue = Color(0xFF80B5DF)
private val Muted = Color(0xFF9692A5)
private val MainText = Color(0xFFF4F0FB)

data class Wish(val id: String, val name: String, val type: String, val rarity: Int, val time: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GIWCApp() }
    }
}

@Composable
fun GIWCApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("giwc_local", Context.MODE_PRIVATE) }
    var uid by remember { mutableStateOf(prefs.getString("uid", "") ?: "") }
    var server by remember { mutableStateOf(prefs.getString("server", "Europe") ?: "Europe") }
    var wishes by remember { mutableStateOf(loadWishes(prefs)) }
    var page by remember { mutableStateOf("Overview") }
    var language by remember { mutableStateOf("EN") }
    var message by remember { mutableStateOf("") }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val raw = context.contentResolver.openInputStream(uri)?.use { input ->
                    BufferedReader(InputStreamReader(input)).readText()
                } ?: error("Cannot read file")
                val parsed = parseUigf(JSONObject(raw))
                val merged = (wishes + parsed).distinctBy { it.id }.sortedBy { it.time }
                wishes = merged
                saveWishes(prefs, merged)
                message = "Imported ${parsed.size} records. Saved locally."
            }.onFailure { message = "Import failed: ${it.message ?: "Invalid UIGF JSON"}" }
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Purple, background = Bg, surface = Panel, onBackground = MainText,
        onSurface = MainText, secondary = Gold
    )) {
        Scaffold(containerColor = Bg) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("GIWC ✦", fontSize = 26.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp, color = MainText)
                            Text("GENSHIN IMPACT WISH COUNTER", fontSize = 9.sp, letterSpacing = 2.sp, color = Muted)
                        }
                        OutlinedButton(onClick = { language = if (language == "EN") "RU" else "EN" }) { Text(language) }
                    }
                }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Panel2), shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.padding(20.dp)) {
                            Text(if (language == "EN") "Your wishes. Your history." else "Твои молитвы. Твоя история.", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(5.dp))
                            Text(if (language == "EN") "Track pity, guarantee and pull history." else "Отслеживай pity, гарант и историю выпадений.", color = Muted)
                        }
                    }
                }
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Overview", "Connect", "History", "Statistics").forEach { tab ->
                            FilterChip(selected = page == tab, onClick = { page = tab }, label = { Text(if (language == "RU") mapOf("Overview" to "Обзор", "Connect" to "Подключение", "History" to "История", "Statistics" to "Статистика")[tab]!! else tab) })
                        }
                    }
                }
                when (page) {
                    "Connect" -> {
                        item { SectionTitle(if (language == "EN") "ACCOUNT PROFILE" else "ПРОФИЛЬ АККАУНТА") }
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    OutlinedTextField(value = uid, onValueChange = { uid = it.filter(Char::isDigit).take(10) }, label = { Text("Game UID") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                                    var expanded by remember { mutableStateOf(false) }
                                    Box {
                                        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text("Server: $server") }
                                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                            listOf("Europe", "America", "Asia", "TW / HK / MO").forEach { value ->
                                                DropdownMenuItem(text = { Text(value) }, onClick = { server = value; expanded = false })
                                            }
                                        }
                                    }
                                    Button(onClick = {
                                        prefs.edit().putString("uid", uid).putString("server", server).apply()
                                        message = "Profile saved on this device."
                                    }, modifier = Modifier.fillMaxWidth()) { Text("Save profile") }
                                    Text("A UID alone cannot reveal private wish history. GIWC does not ask for your HoYoverse password or cookies.", color = Muted, fontSize = 12.sp)
                                }
                            }
                        }
                        item { SectionTitle("IMPORT WISH HISTORY") }
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("Choose a UIGF JSON export. Imported records are stored only on this device.", color = Muted)
                                    Button(onClick = { importLauncher.launch(arrayOf("application/json", "text/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Import UIGF JSON") }
                                    OutlinedButton(onClick = {
                                        wishes = demoWishes()
                                        saveWishes(prefs, wishes)
                                        message = "Demo data loaded (fictional)."
                                    }, modifier = Modifier.fillMaxWidth()) { Text("Load demo data") }
                                    OutlinedButton(onClick = {
                                        wishes = emptyList()
                                        saveWishes(prefs, wishes)
                                        message = "Local wish history cleared."
                                    }, modifier = Modifier.fillMaxWidth()) { Text("Clear local history") }
                                    Text("Live automatic retrieval is not enabled in this build. It requires a reviewed secure integration; temporary access keys must never be sent to an untrusted server.", color = Gold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    "History" -> {
                        item { SectionTitle("WISH HISTORY · ${wishes.size}") }
                        if (wishes.isEmpty()) item { EmptyState("No wish records. Import a UIGF JSON file to begin.") }
                        items(wishes.sortedByDescending { it.time }.take(250)) { wish ->
                            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(12.dp)) {
                                Row(Modifier.fillMaxWidth().padding(13.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(wish.name, fontWeight = FontWeight.SemiBold)
                                        Text("${bannerName(wish.type)} · ${wish.time}", color = Muted, fontSize = 11.sp)
                                    }
                                    Text("${wish.rarity}★", color = if (wish.rarity == 5) Gold else if (wish.rarity == 4) Purple else Blue, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    "Statistics" -> {
                        item { SectionTitle("PULL ANALYTICS") }
                        item {
                            val fives = wishes.count { it.rarity == 5 }
                            val fours = wishes.count { it.rarity == 4 }
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    MetricCard("Total wishes", wishes.size.toString(), Modifier.weight(1f))
                                    MetricCard("5★ items", fives.toString(), Modifier.weight(1f), Gold)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    MetricCard("4★ items", fours.toString(), Modifier.weight(1f), Purple)
                                    MetricCard("Other", (wishes.size - fives - fours).toString(), Modifier.weight(1f), Blue)
                                }
                                Text("Rarity rates are descriptive only; imported history may cover a limited period.", color = Muted, fontSize = 12.sp)
                            }
                        }
                    }
                    else -> {
                        item { SectionTitle("WISH COUNTERS") }
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    PityCard("Character Event", pity(wishes, setOf("301", "400")), "90", Modifier.weight(1f), Purple)
                                    PityCard("Weapon Event", pity(wishes, setOf("302")), "80", Modifier.weight(1f), Gold)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    PityCard("Standard", pity(wishes, setOf("200")), "90", Modifier.weight(1f), Blue)
                                    PityCard("Chronicled", pity(wishes, setOf("500")), "90", Modifier.weight(1f), Purple)
                                }
                            }
                        }
                        item { SectionTitle("GUARANTEE STATUS") }
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                                Column(Modifier.padding(16.dp)) {
                                    Text("Character Event", fontWeight = FontWeight.SemiBold)
                                    Text("Pity: ${pity(wishes, setOf("301", "400"))} / 90", color = Purple, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(5.dp))
                                    Text("50/50 status needs featured-item classification and sufficient banner history. GIWC will not guess a guarantee.", color = Muted, fontSize = 12.sp)
                                    if (uid.isNotBlank()) Text("UID $uid · $server", color = Muted, fontSize = 11.sp)
                                }
                            }
                        }
                        item { Text("Import history from the Connect tab to calculate your counters.", color = Muted, fontSize = 12.sp) }
                    }
                }
                if (message.isNotBlank()) item { Text(message, color = Purple, fontSize = 12.sp) }
                item { Text("Unofficial fan-made companion. Not affiliated with HoYoverse. Data stored locally.", color = Muted, fontSize = 10.sp) }
            }
        }
    }
}

@Composable private fun SectionTitle(text: String) { Text(text, color = Color(0xFFC9BFDC), fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold) }
@Composable private fun EmptyState(text: String) { Card(colors = CardDefaults.cardColors(containerColor = Panel)) { Text(text, Modifier.padding(20.dp), color = Muted) } }
@Composable private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier, tint: Color = Purple) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Panel)) { Column(Modifier.padding(14.dp)) { Text(label, color = Muted, fontSize = 11.sp); Text(value, color = tint, fontSize = 27.sp, fontWeight = FontWeight.Bold) } }
}
@Composable private fun PityCard(label: String, pity: Int?, max: String, modifier: Modifier, tint: Color) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Panel)) {
        Column(Modifier.padding(13.dp)) {
            Text(label, color = Muted, fontSize = 11.sp, minLines = 2)
            Text(if (pity == null) "—" else "$pity / $max", color = tint, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            LinearProgressIndicator(progress = { if (pity == null) 0f else (pity.toFloat() / max.toFloat()).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 7.dp), color = tint, trackColor = Color(0xFF393545))
        }
    }
}
private fun pity(wishes: List<Wish>, types: Set<String>): Int? {
    val pool = wishes.filter { it.type in types }.sortedBy { it.time }
    if (pool.isEmpty()) return null
    val lastFive = pool.indexOfLast { it.rarity == 5 }
    return if (lastFive < 0) pool.size else pool.size - lastFive - 1
}
private fun bannerName(type: String) = when(type) { "301", "400" -> "Character Event"; "302" -> "Weapon Event"; "200" -> "Standard"; "500" -> "Chronicled"; "100" -> "Beginner"; else -> "Other" }
private fun loadWishes(context: Context): List<Wish> = runCatching {
    val arr = JSONArray(context.getSharedPreferences("giwc_local", Context.MODE_PRIVATE).getString("wishes", "[]"))
    (0 until arr.length()).map { i -> arr.getJSONObject(i).toWish() }
}.getOrDefault(emptyList())
private fun saveWishes(context: Context, wishes: List<Wish>) {
    val arr = JSONArray()
    wishes.forEach { w -> arr.put(JSONObject().put("id", w.id).put("name", w.name).put("type", w.type).put("rarity", w.rarity).put("time", w.time)) }
    context.getSharedPreferences("giwc_local", Context.MODE_PRIVATE).edit().putString("wishes", arr.toString()).apply()
}
private fun JSONObject.toWish() = Wish(optString("id"), optString("name"), optString("type"), optInt("rarity"), optString("time"))
private fun parseUigf(root: JSONObject): List<Wish> {
    val arrays = mutableListOf<JSONArray>()
    fun walk(value: Any?) {
        when (value) {
            is JSONObject -> {
                val list = value.optJSONArray("list")
                if (list != null && list.length() > 0 && list.optJSONObject(0)?.has("gacha_type") == true) arrays.add(list)
                val keys = value.keys()
                while (keys.hasNext()) walk(value.opt(keys.next()))
            }
            is JSONArray -> for (i in 0 until value.length()) walk(value.opt(i))
        }
    }
    walk(root)
    val source = arrays.firstOrNull() ?: root.optJSONArray("list") ?: root.optJSONArray("gacha") ?: JSONArray()
    val out = mutableListOf<Wish>()
    for (i in 0 until source.length()) {
        val o = source.optJSONObject(i) ?: continue
        val type = o.optString("gacha_type", o.optString("uigf_gacha_type", ""))
        if (type !in setOf("100", "200", "301", "302", "400", "500")) continue
        val rarity = o.optString("rank_type", o.optString("rarity", "0")).toIntOrNull() ?: 0
        out.add(Wish(o.optString("id", "$type-$i-${o.optString("time")}"), o.optString("name", o.optString("item_name", "Unknown item")), type, rarity, o.optString("time")))
    }
    return out
}
private fun demoWishes(): List<Wish> {
    val now = System.currentTimeMillis()
    val out = mutableListOf<Wish>()
    listOf("301" to 42, "302" to 17, "200" to 63).forEach { (type, count) ->
        for (i in 0 until count) {
            val rarity = if (i == count - 1) 5 else if (i % 10 == 0) 4 else 3
            out.add(Wish("$type-$i", if (rarity == 5) "Demo 5★ Item" else if (rarity == 4) "Demo 4★ Item" else "Demo 3★ Item", type, rarity, java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date(now - (count-i)*86400000L))))
        }
    }
    return out
}
