package com.thepagination.mhanalyst.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavHostController
import com.thepagination.mhanalyst.data.ApiException
import com.thepagination.mhanalyst.data.AppViewModel
import com.thepagination.mhanalyst.data.LastPrice
import com.thepagination.mhanalyst.data.Ledger
import com.thepagination.mhanalyst.data.StockItem
import com.thepagination.mhanalyst.util.Fmt
import com.thepagination.mhanalyst.util.Fuzzy
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

private class ItemLine {
    var name by mutableStateOf("")
    var qty by mutableStateOf("")
    var rate by mutableStateOf("")
    var disc by mutableStateOf("")
    var autoPrice by mutableStateOf(true)
    fun amount(): Double = Fmt.r2(num(qty) * num(rate) * (1 - num(disc) / 100))
}

private fun num(s: String): Double = s.replace(",", "").trim().toDoubleOrNull() ?: 0.0

/** Picks the ledger whose name carries the rate, e.g. "Sales 18%" or "SGST 9%". */
private fun pickByRate(names: List<String>, rate: Double?, word: Regex?): String {
    val list = if (word != null) names.filter { word.containsMatchIn(it) } else names
    if (list.isEmpty()) return ""
    if (rate != null) {
        val r = Fmt.qty(rate)
        val re = Regex("(^|[^\\d.])" + Regex.escape(r) + "\\s*%")
        list.firstOrNull { re.containsMatchIn(it) }?.let { return it }
    }
    return list.first()
}

@Composable
fun NewVoucherScreen(vm: AppViewModel, nav: NavHostController) {
    val p = vm.profile ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dark = isSystemInDarkTheme()

    var purchase by remember { mutableStateOf(false) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var party by remember { mutableStateOf("") }
    var main by remember { mutableStateOf("") }
    var mainAuto by remember { mutableStateOf(true) }
    var reference by remember { mutableStateOf("") }
    var narration by remember { mutableStateOf("") }
    val lines = remember { mutableStateListOf(ItemLine()) }
    var gstOn by remember { mutableStateOf(true) }
    var gstRate by remember { mutableStateOf(18.0) }
    var inter by remember { mutableStateOf(false) }
    var sgst by remember { mutableStateOf("") }
    var cgst by remember { mutableStateOf("") }
    var igst by remember { mutableStateOf("") }
    var roundOn by remember { mutableStateOf(true) }
    var roundLedger by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var picker by remember { mutableStateOf<Pair<String, (String) -> Unit>?>(null) }
    val clientRef = remember { UUID.randomUUID().toString().replace("-", "") }

    val ledgers: List<Ledger> = load(vm.version, vm.company) { vm.repo.db.ledgers(vm.company) } ?: emptyList()
    val items: List<StockItem> = load(vm.version, vm.company) { vm.repo.db.stock(vm.company) { p.hidden(it) } } ?: emptyList()
    val anyPrices: Map<String, LastPrice> = load(vm.version, vm.company, purchase) { vm.repo.db.lastPrices(vm.company, purchase, null) } ?: emptyMap()
    val partyPrices: Map<String, LastPrice> = load(vm.version, vm.company, purchase, party) { if (party.isEmpty()) emptyMap() else vm.repo.db.lastPrices(vm.company, purchase, party) } ?: emptyMap()

    val names = ledgers.map { it.name }
    val balances = ledgers.associate { it.name to it.cb }
    val mains = ledgers.filter { it.parent.contains(if (purchase) "purchase" else "sale", ignoreCase = true) }.map { it.name }.ifEmpty { names }
    val taxes = ledgers.filter { it.parent.contains("dut", ignoreCase = true) }.map { it.name }.ifEmpty { names }
    val itemInfo = items.associateBy { it.name }

    // Choose ledgers automatically until the person picks one.
    LaunchedEffect(gstOn, gstRate, purchase, ledgers.size) {
        if (mainAuto) main = if (gstOn) pickByRate(mains, gstRate, null) else (mains.firstOrNull { Regex("exempt|nil|non[- ]?gst", RegexOption.IGNORE_CASE).containsMatchIn(it) } ?: main.ifEmpty { mains.firstOrNull() ?: "" })
        sgst = pickByRate(taxes, gstRate / 2, Regex("\\b(SGST|UTGST)\\b", RegexOption.IGNORE_CASE))
        cgst = pickByRate(taxes, gstRate / 2, Regex("\\bCGST\\b", RegexOption.IGNORE_CASE))
        igst = pickByRate(taxes, gstRate, Regex("\\bIGST\\b", RegexOption.IGNORE_CASE))
        if (roundLedger.isEmpty()) roundLedger = names.firstOrNull { it.contains("round", ignoreCase = true) } ?: ""
    }
    // Fill in the last price (for this party if known) unless the person typed their own.
    LaunchedEffect(partyPrices, anyPrices) {
        lines.forEach { l ->
            if (l.name.isNotEmpty() && (l.rate.isEmpty() || l.autoPrice)) {
                (partyPrices[l.name] ?: anyPrices[l.name])?.let { lp ->
                    l.rate = Fmt.qty(lp.rate)
                    l.disc = if (lp.disc > 0) Fmt.qty(lp.disc) else ""
                    l.autoPrice = true
                }
            }
        }
    }

    val amounts = lines.map { it.amount() }
    val itemsTotal = Fmt.r2(amounts.sum())
    val perLine = { pct: Double -> Fmt.r2(amounts.sumOf { Fmt.r2(it * pct / 100) }) }
    val half = if (gstOn && !inter) perLine(gstRate / 2) else 0.0
    val igstAmt = if (gstOn && inter) perLine(gstRate) else 0.0
    val before = Fmt.r2(itemsTotal + 2 * half + igstAmt)
    val roundAmt = if (roundOn) Fmt.r2(Math.round(before).toDouble() - before) else 0.0
    val total = Fmt.r2(before + roundAmt)

    val sheet = when {
        purchase && dark -> Color(0xFF2A1D12)
        purchase -> Color(0xFFFFF3E6)
        dark -> Color(0xFF16211A)
        else -> Color(0xFFF4FBE9)
    }

    Page("New voucher", vm, nav, back = true, subtitle = vm.companyName) {
        item {
            DCard(background = sheet) {
                Segmented(listOf("Sales" to "Sales", "Purchase" to "Purchase"), if (purchase) "Purchase" else "Sales", { purchase = it == "Purchase"; mainAuto = true }, Modifier.fillMaxWidth())
                PickField("Date", Fmt.date(date.toString()) + " · " + date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }) {
                    DatePickerDialog(context, { _, y, m, d -> date = LocalDate.of(y, m + 1, d) }, date.year, date.monthValue - 1, date.dayOfMonth).show()
                }
                PickField("Party A/c name", party.ifEmpty { "Choose party" }, balances[party]?.let { "Current balance " + Fmt.drcr(it) }) {
                    picker = "Party" to { v: String -> party = v }
                }
                PickField(if (purchase) "Purchase ledger" else "Sales ledger", main.ifEmpty { "Choose ledger" }, balances[main]?.let { "Current balance " + Fmt.drcr(it) }) {
                    picker = "Ledger" to { v: String -> main = v; mainAuto = false }
                }
                OutlinedTextField(reference, { reference = it }, label = { Text("Reference No (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        }
        item {
            DCard(background = sheet) {
                CardTitle("Items")
                lines.forEachIndexed { i, l ->
                    if (i > 0) HorizontalDivider(color = mh.line)
                    val info = itemInfo[l.name]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            PickField("Name of item", l.name.ifEmpty { "Choose item" }) {
                                picker = "Item" to { v: String ->
                                    l.name = v
                                    l.autoPrice = true
                                    (partyPrices[v] ?: anyPrices[v])?.let { lp -> l.rate = Fmt.qty(lp.rate); l.disc = if (lp.disc > 0) Fmt.qty(lp.disc) else "" }
                                    if (lines.last() === l) lines.add(ItemLine())
                                }
                            }
                        }
                        if (lines.size > 1) IconButton(onClick = { lines.remove(l) }) { Icon(Icons.Outlined.Close, contentDescription = "Remove item") }
                    }
                    if (l.name.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumField("Qty" + (info?.unit?.let { " ($it)" } ?: ""), l.qty, Modifier.weight(1f)) { l.qty = it }
                            NumField("Rate", l.rate, Modifier.weight(1f)) { l.rate = it; l.autoPrice = false }
                            NumField("Disc %", l.disc, Modifier.weight(0.8f)) { l.disc = it; l.autoPrice = false }
                        }
                        val hints = buildList {
                            partyPrices[l.name]?.let { add("This party: ₹" + Fmt.inr(it.rate) + (if (it.disc > 0) " less " + Fmt.qty(it.disc) + "%" else "") to it) }
                            anyPrices[l.name]?.let { if (partyPrices[l.name]?.date != it.date) add("Last: ₹" + Fmt.inr(it.rate) + " (" + it.party + ")" to it) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            hints.forEach { (label, lp) -> Hint(label) { l.rate = Fmt.qty(lp.rate); l.disc = if (lp.disc > 0) Fmt.qty(lp.disc) else ""; l.autoPrice = false } }
                            info?.mrp?.let { m -> Hint("MRP ₹" + Fmt.inr(m)) { l.rate = Fmt.qty(m); l.autoPrice = false } }
                        }
                        Text("Amount ₹" + Fmt.inr(l.amount()), color = mh.ink, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.End))
                    }
                }
            }
        }
        item {
            DCard(background = sheet) {
                TotalRow("Items", itemsTotal)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = gstOn, onCheckedChange = { gstOn = it })
                    Spacer(Modifier.width(10.dp))
                    Text("GST", color = mh.ink, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                }
                if (gstOn) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5.0, 12.0, 18.0, 28.0).forEach { r -> Pill(Fmt.qty(r) + "%", gstRate == r) { gstRate = r } }
                    }
                    Segmented(listOf("intra" to "Same state", "inter" to "Other state"), if (inter) "inter" else "intra", { inter = it == "inter" }, Modifier.fillMaxWidth())
                    if (!inter) {
                        TaxRow(sgst.ifEmpty { "Choose SGST ledger" }, Fmt.qty(gstRate / 2) + " %", half) { picker = "SGST ledger" to { v: String -> sgst = v } }
                        TaxRow(cgst.ifEmpty { "Choose CGST ledger" }, Fmt.qty(gstRate / 2) + " %", half) { picker = "CGST ledger" to { v: String -> cgst = v } }
                    } else {
                        TaxRow(igst.ifEmpty { "Choose IGST ledger" }, Fmt.qty(gstRate) + " %", igstAmt) { picker = "IGST ledger" to { v: String -> igst = v } }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = roundOn, onCheckedChange = { roundOn = it })
                    Spacer(Modifier.width(10.dp))
                    Text(roundLedger.ifEmpty { "Round off" }, color = mh.ink, modifier = Modifier.weight(1f).clickable { picker = "Round off ledger" to { v: String -> roundLedger = v } })
                    Text(Fmt.inr(roundAmt), color = mh.ink)
                }
                HorizontalDivider(color = mh.line)
                Row {
                    Text("Total", color = mh.ink, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    Text("₹" + Fmt.inr(total), color = mh.ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                OutlinedTextField(narration, { narration = it }, label = { Text("Narration") }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            }
        }
        item {
            error?.let { Text(it, color = mh.orange, modifier = Modifier.padding(bottom = 8.dp)) }
            Button(
                onClick = {
                    val filled = lines.filter { it.name.isNotEmpty() }
                    error = when {
                        party.isEmpty() -> "Choose the party."
                        main.isEmpty() -> "Choose the " + (if (purchase) "purchase" else "sales") + " ledger."
                        filled.isEmpty() -> "Add at least one item."
                        filled.any { num(it.qty) <= 0 } -> "Every item needs a quantity."
                        else -> null
                    }
                    if (error != null) return@Button
                    busy = true
                    val body = JSONObject()
                        .put("vch_type", if (purchase) "Purchase" else "Sales").put("vch_date", date.toString())
                        .put("party_ledger", party).put("main_ledger", main).put("reference", reference).put("narration", narration)
                        .put("client_ref", clientRef)
                        .put("items", JSONArray().apply { filled.forEach { put(JSONObject().put("stock_item", it.name).put("qty", num(it.qty)).put("rate", num(it.rate)).put("disc", num(it.disc))) } })
                        .put("gst", JSONObject().put("on", gstOn).put("rate", gstRate).put("mode", if (inter) "inter" else "intra").put("sgst", sgst).put("cgst", cgst).put("igst", igst))
                        .put("round", JSONObject().put("on", roundOn).put("ledger", roundLedger))
                    scope.launch {
                        try {
                            val res = vm.repo.createVoucher(vm.company, body)
                            vm.message("Saved as WEB-" + res.optInt("id") + " (₹" + Fmt.inr(res.optDouble("total")) + "). It goes into Tally on the next sync.")
                            vm.afterVoucherSaved()
                            nav.popBackStack()
                            nav.navigate("web")
                        } catch (e: ApiException) {
                            error = e.message
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (purchase) Color(0xFFC2620A) else mh.accent),
            ) { Text(if (busy) "Saving…" else "Save " + (if (purchase) "purchase" else "sales") + " voucher · ₹" + Fmt.inr(total), fontWeight = FontWeight.Bold) }
        }
    }

    picker?.let { (title, onPick) ->
        val options: List<Pair<String, String>> = when (title) {
            "Item" -> items.map { it.name to (Fmt.qty(it.cq) + " " + it.unit + (it.mrp?.let { m -> " · MRP " + Fmt.inr(m) } ?: "")) }
            "Party" -> ledgers.map { it.name to Fmt.drcr(it.cb) }
            "Ledger" -> mains.map { it to (balances[it]?.let { b -> Fmt.drcr(b) } ?: "") }
            "SGST ledger", "CGST ledger", "IGST ledger" -> taxes.map { it to "" }
            else -> names.map { it to "" }
        }
        PickerDialog(title, options, onPick = { onPick(it); picker = null }, onDismiss = { picker = null })
    }
}

@Composable
private fun PickField(label: String, value: String, note: String? = null, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(mh.panel).border(1.dp, mh.line, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Text(label, color = mh.muted, fontSize = 12.sp)
        Text(value, color = mh.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        note?.let { Text(it, color = mh.muted, fontSize = 12.sp) }
    }
}

@Composable
private fun NumField(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, { v -> onChange(v.filter { it.isDigit() || it == '.' }) }, label = { Text(label, maxLines = 1) }, singleLine = true, modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

@Composable
private fun Hint(label: String, onClick: () -> Unit) {
    Text(
        label, color = mh.accent, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(mh.panel).border(1.dp, mh.line, RoundedCornerShape(50)).clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun TotalRow(label: String, value: Double) {
    Row {
        Text(label, color = mh.muted, modifier = Modifier.weight(1f))
        Text("₹" + Fmt.inr(value), color = mh.ink, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TaxRow(ledger: String, pct: String, amount: Double, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(ledger, color = mh.ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).clickable(onClick = onClick))
        Text(pct, color = mh.muted, modifier = Modifier.width(56.dp))
        Text(Fmt.inr(amount), color = mh.ink, fontWeight = FontWeight.Bold)
    }
}

/** Full list with forgiving search: "1cap cpvc" finds "1 CPVC CAP". */
@Composable
private fun PickerDialog(title: String, options: List<Pair<String, String>>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val list = remember(q, options) { Fuzzy.filter(options, q) { it.first }.take(80) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(22.dp), color = mh.panel, modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = mh.ink, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, contentDescription = "Close") }
                }
                SearchField(q, { q = it }, "Type to search", Modifier.focusRequester(focus))
                LazyColumn(Modifier.padding(top = 8.dp)) {
                    items(list) { (name, meta) ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onPick(name) }.padding(horizontal = 8.dp, vertical = 11.dp)) {
                            Text(name, color = mh.ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            if (meta.isNotEmpty()) Text(meta, color = mh.muted, fontSize = 12.sp)
                        }
                    }
                    if (list.isEmpty()) item { EmptyNote("No match. Check the spelling or the Tally name.") }
                }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Cancel") }
            }
        }
    }
}
