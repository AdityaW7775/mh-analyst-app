package com.thepagination.mhanalyst.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.thepagination.mhanalyst.data.AppViewModel
import com.thepagination.mhanalyst.data.StockItem
import com.thepagination.mhanalyst.util.Fmt
import com.thepagination.mhanalyst.util.Fuzzy
import java.time.LocalDate
import kotlin.math.abs

private fun fyStart(): LocalDate { val t = LocalDate.now(); return LocalDate.of(if (t.monthValue >= 4) t.year else t.year - 1, 4, 1) }

private val periods = listOf("month" to "This month", "last" to "Last month", "3m" to "3 months", "fy" to "This FY", "all" to "All")
private fun periodRange(key: String): Pair<String, String> {
    val t = LocalDate.now()
    return when (key) {
        "last" -> t.withDayOfMonth(1).minusMonths(1).toString() to t.withDayOfMonth(1).minusDays(1).toString()
        "3m" -> t.minusMonths(3).toString() to t.toString()
        "fy" -> fyStart().toString() to t.toString()
        "all" -> "1900-01-01" to "2999-12-31"
        else -> t.withDayOfMonth(1).toString() to t.toString()
    }
}

@Composable
private fun PillRow(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (k, label) -> Pill(label, k == selected) { onSelect(k) } }
    }
}

@Composable
private fun KeyValue(k: String, v: String, modifier: Modifier = Modifier, vColor: Color = mh.ink) {
    Column(modifier) {
        Text(k, color = mh.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(v, color = vColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---------- Day Book ----------

@Composable
fun DayBookScreen(vm: AppViewModel, nav: NavHostController) {
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val rows = load(vm.version, vm.company, date) { vm.repo.db.dayBook(vm.company, date) } ?: emptyList()
    val last = load(vm.version, vm.company, date, rows.isEmpty()) { if (rows.isEmpty()) vm.repo.db.lastVoucherDate(vm.company, date) else null }
    val d = LocalDate.parse(date)
    Page("Day Book", vm, nav) {
        item {
            DCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { date = d.minusDays(1).toString() }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Previous day") }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(Fmt.date(date), color = mh.ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(d.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() } + " · " + rows.size + " vouchers", color = mh.muted, fontSize = 13.sp)
                    }
                    IconButton(onClick = { date = d.plusDays(1).toString() }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Next day") }
                }
                if (date != LocalDate.now().toString()) TextButton(onClick = { date = LocalDate.now().toString() }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Today") }
            }
        }
        if (rows.isEmpty()) {
            item {
                DCard {
                    EmptyNote("No vouchers on this date.")
                    if (last != null) TextButton(onClick = { date = last }) { Text("Go to the last day with vouchers: " + Fmt.date(last)) }
                }
            }
        } else {
            item { DCard { rows.forEach { VoucherLine(it) { nav.open("voucher", it.guid) } } } }
            item {
                DCard {
                    CardTitle("Totals by voucher type")
                    rows.filter { !it.cancelled && !it.optional }.groupBy { it.type }.forEach { (type, list) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(type, color = mh.ink, modifier = Modifier.weight(1f))
                            Text("${list.size}  ·  ₹" + Fmt.inr(list.sumOf { it.amount }), color = mh.ink, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

// ---------- Vouchers ----------

@Composable
fun VouchersScreen(vm: AppViewModel, nav: NavHostController) {
    var period by rememberSaveable { mutableStateOf("month") }
    var type by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var limit by rememberSaveable { mutableIntStateOf(100) }
    val (from, to) = periodRange(period)
    val types = load(vm.version, vm.company) { vm.repo.db.voucherTypes(vm.company) } ?: emptyList()
    val all = load(vm.version, vm.company, period, type) { vm.repo.db.vouchers(vm.company, from, to, type.ifEmpty { null }) } ?: emptyList()
    val list = remember(all, query) { Fuzzy.filter(all, query) { it.party + " " + it.no + " " + it.reference + " " + it.narration } }
    Page("Vouchers", vm, nav) {
        item { SearchField(query, { query = it; limit = 100 }, "Party, number, reference or narration") }
        item { PillRow(periods, period) { period = it; limit = 100 } }
        if (types.size > 1) item { PillRow(listOf("" to "All types") + types.map { it to it }, type) { type = it; limit = 100 } }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                Text("${list.size} vouchers", color = mh.muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text("₹" + Fmt.inr(list.filter { !it.cancelled && !it.optional }.sumOf { it.amount }), color = mh.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        item {
            DCard {
                if (list.isEmpty()) EmptyNote(if (query.isBlank()) "No vouchers in this period." else "No vouchers match. Try fewer letters.")
                list.take(limit).forEach { VoucherLine(it) { nav.open("voucher", it.guid) } }
                if (list.size > limit) TextButton(onClick = { limit += 200 }) { Text("Show more (${list.size - limit} left)") }
            }
        }
    }
}

@Composable
fun VoucherScreen(vm: AppViewModel, nav: NavHostController, guid: String) {
    val data = load(vm.version, vm.company, guid) { vm.repo.db.voucher(vm.company, guid) }
    if (data == null) {
        Page("Voucher", vm, nav, back = true) { item { DCard { EmptyNote("This voucher isn't on the phone. It may have been deleted in Tally.") } } }
        return
    }
    val (v, lines, inv) = data
    Page(v.type + if (v.no.isNotEmpty()) " No. " + v.no else "", vm, nav, back = true, subtitle = Fmt.date(v.date) + (if (v.cancelled) " · Cancelled" else "")) {
        item {
            DCard {
                CardTitle("Amount")
                BigAmount(v.amount)
                val partyGuid = load(v.party) { vm.repo.db.ledgerGuid(vm.company, v.party) }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Column(Modifier.weight(1f).clickable(enabled = partyGuid != null) { partyGuid?.let { nav.open("ledger", it) } }) {
                        Text("Party", color = mh.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(v.party.ifEmpty { "–" }, color = if (partyGuid != null) mh.accent else mh.ink, fontWeight = FontWeight.SemiBold)
                    }
                    KeyValue("Reference", v.reference.ifEmpty { "–" }, Modifier.weight(1f))
                }
                if (v.narration.isNotBlank()) KeyValue("Narration", v.narration, Modifier.padding(top = 6.dp))
            }
        }
        if (inv.isNotEmpty()) item {
            DCard {
                CardTitle("Items")
                inv.forEach { it2 ->
                    val g = load(it2.item) { vm.repo.db.itemGuid(vm.company, it2.item) }
                    Column(Modifier.fillMaxWidth().clickable(enabled = g != null) { g?.let { nav.open("item", it) } }.padding(vertical = 8.dp)) {
                        Row {
                            Text(it2.item, color = mh.ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text("₹" + Fmt.inr(abs(it2.amount)), color = mh.ink, fontWeight = FontWeight.Bold)
                        }
                        Text(Fmt.qty(it2.qty) + " " + it2.unit + " × ₹" + Fmt.inr(it2.rate) + (if (it2.disc != 0.0) " less " + Fmt.qty(it2.disc) + "%" else "") +
                            (if (it2.godown.isNotEmpty()) " · " + it2.godown else ""), color = mh.muted, fontSize = 12.sp)
                    }
                }
            }
        }
        item {
            DCard {
                CardTitle("Accounting entries")
                Row(Modifier.fillMaxWidth()) {
                    Text("Ledger", color = mh.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text("Debit", color = mh.muted, fontSize = 12.sp, modifier = Modifier.width(96.dp))
                    Text("Credit", color = mh.muted, fontSize = 12.sp, modifier = Modifier.width(96.dp))
                }
                lines.forEach { l ->
                    HorizontalDivider(color = mh.line)
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text(l.ledger, color = mh.ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text(if (l.amount < 0) Fmt.inr(-l.amount) else "", color = mh.ink, fontSize = 14.sp, modifier = Modifier.width(96.dp))
                        Text(if (l.amount > 0) Fmt.inr(l.amount) else "", color = mh.ink, fontSize = 14.sp, modifier = Modifier.width(96.dp))
                    }
                }
            }
        }
    }
}

// ---------- Ledgers ----------

@Composable
fun LedgersScreen(vm: AppViewModel, nav: NavHostController) {
    var query by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf("") }
    var limit by rememberSaveable { mutableIntStateOf(150) }
    val all = load(vm.version, vm.company) { vm.repo.db.ledgers(vm.company) } ?: emptyList()
    val groups = remember(all) { all.groupingBy { it.parent }.eachCount().entries.sortedByDescending { it.value }.map { it.key }.filter { it.isNotEmpty() } }
    val list = remember(all, query, group) { Fuzzy.filter(if (group.isEmpty()) all else all.filter { it.parent == group }, query) { it.name + " " + it.gstin + " " + it.mobile } }
    Page("Ledgers", vm, nav) {
        item { SearchField(query, { query = it; limit = 150 }, "Name, GSTIN or phone") }
        item { PillRow(listOf("" to "All") + groups.map { it to it }, group) { group = it; limit = 150 } }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                Text("${list.size} ledgers", color = mh.muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                if (group.isNotEmpty()) Text("Net " + Fmt.drcr(list.sumOf { it.cb }), color = mh.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        items(list.take(limit), key = { it.guid }) { l ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(mh.panel).clickable { nav.open("ledger", l.guid) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(l.name, color = mh.ink, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(l.parent, color = mh.muted, fontSize = 12.sp, maxLines = 1)
                }
                Text(Fmt.drcr(l.cb), color = mh.ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
        if (list.size > limit) item { TextButton(onClick = { limit += 300 }) { Text("Show more (${list.size - limit} left)") } }
    }
}

@Composable
fun LedgerScreen(vm: AppViewModel, nav: NavHostController, guid: String) {
    var period by rememberSaveable { mutableStateOf("fy") }
    val context = LocalContext.current
    val l = load(vm.version, vm.company, guid) { vm.repo.db.ledgerByGuid(vm.company, guid) }
    if (l == null) {
        Page("Ledger", vm, nav, back = true) { item { DCard { EmptyNote("Loading…") } } }
        return
    }
    val (from, to) = periodRange(period)
    val data = load(vm.version, vm.company, guid, period) { vm.repo.db.ledgerEntries(vm.company, l.name, l.ob, from, to) }
    Page(l.name, vm, nav, back = true, subtitle = l.parent) {
        item {
            DCard {
                CardTitle("Closing balance in Tally")
                Text("₹" + Fmt.drcr(l.cb), color = mh.ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    if (l.gstin.isNotEmpty()) KeyValue("GSTIN", l.gstin, Modifier.weight(1f))
                    if (l.mobile.isNotEmpty()) Column(Modifier.weight(1f).clickable {
                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + l.mobile.filter { it.isDigit() || it == '+' })))
                    }) {
                        Text("Phone", color = mh.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(l.mobile, color = mh.accent, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (l.address.isNotBlank() || l.state.isNotBlank()) KeyValue("Address", (l.address + "\n" + l.state).trim(), Modifier.padding(top = 6.dp))
            }
        }
        item { PillRow(periods.filter { it.first != "last" }, period) { period = it } }
        if (data != null) item {
            val (opening, entries) = data
            var bal = opening
            DCard {
                Row(Modifier.fillMaxWidth()) {
                    Text("Opening balance", color = mh.muted, modifier = Modifier.weight(1f))
                    Text(Fmt.drcr(opening), color = mh.muted)
                }
                if (entries.isEmpty()) EmptyNote("No vouchers for this ledger in this period.")
                entries.forEach { e ->
                    bal += e.amount
                    HorizontalDivider(color = mh.line)
                    Row(Modifier.fillMaxWidth().clickable { nav.open("voucher", e.guid) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(e.particulars.ifEmpty { e.type }, color = mh.ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(Fmt.date(e.date) + " · " + e.type + " " + e.no, color = mh.muted, fontSize = 12.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text((if (e.amount < 0) "Dr " else "Cr ") + Fmt.inr(abs(e.amount)), color = if (e.amount < 0) mh.ink else mh.green, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(Fmt.drcr(bal), color = mh.muted, fontSize = 12.sp)
                        }
                    }
                }
                HorizontalDivider(color = mh.line)
                Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Text("Closing balance", color = mh.ink, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(Fmt.drcr(bal), color = mh.ink, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ---------- Stock ----------

@Composable
fun StockScreen(vm: AppViewModel, nav: NavHostController) {
    val p = vm.profile ?: return
    var query by rememberSaveable { mutableStateOf("") }
    var chip by rememberSaveable { mutableStateOf("all") }
    var limit by rememberSaveable { mutableIntStateOf(80) }
    val all = load(vm.version, vm.company) { vm.repo.db.stock(vm.company) { p.hidden(it) } } ?: emptyList()
    val list = remember(all, query, chip) {
        val base = when (chip) {
            "in" -> all.filter { it.cq > 0 }
            "low" -> all.filter { it.cq > 0 && it.sold30 > 0 && it.cq < it.sold30 }
            "out" -> all.filter { it.cq <= 0 }
            "top" -> all.filter { it.sold90 > 0 }.sortedByDescending { it.sold90 }
            else -> all
        }
        Fuzzy.filter(base, query) { it.name }
    }
    Page("Stock", vm, nav) {
        item { SearchField(query, { query = it; limit = 80 }, "Search items: typos and any word order are fine") }
        item { PillRow(listOf("all" to "All", "in" to "In stock", "low" to "Running low", "out" to "Out of stock", "top" to "Top selling"), chip) { chip = it; limit = 80 } }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                Text("${list.size} items", color = mh.muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text("Value ₹" + Fmt.inr(list.sumOf { it.cv }, 0), color = mh.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        items(list.take(limit), key = { it.guid }) { ProductCard(it) { nav.open("item", it.guid) } }
        if (list.size > limit) item { TextButton(onClick = { limit += 200 }) { Text("Show more (${list.size - limit} left)") } }
    }
}

@Composable
private fun ProductCard(s: StockItem, onClick: () -> Unit) {
    val status = when {
        s.cq <= 0 -> Triple("Out of stock", mh.panel2, mh.muted)
        s.sold30 > 0 && s.cq < s.sold30 -> Triple("Running low", Color(0xFFF59E0B), Color(0xFF1F1300))
        else -> Triple("In stock", Color(0xFF20B15A), Color.White)
    }
    val cover = if (s.sold30 > 0) (s.cq / (s.sold30 * 2)).toFloat() else if (s.cq > 0) 1f else 0f
    DCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(s.name, color = mh.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(s.parent, color = mh.muted, fontSize = 12.sp)
            }
            StatusTag(status.first, status.second, status.third)
        }
        Row(Modifier.fillMaxWidth()) {
            Text("Last sold " + (s.lastRate?.let { "₹" + Fmt.inr(it) } ?: "–") + (if (s.lastDisc > 0) " less " + Fmt.qty(s.lastDisc) + "%" else ""), color = mh.muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text("Sold " + Fmt.qty(s.sold30) + " in 30 days", color = mh.muted, fontSize = 13.sp)
        }
        Row(Modifier.fillMaxWidth()) {
            Text(if (s.mrp != null) "MRP/Marginal ₹" + Fmt.inr(s.mrp) else "", color = mh.muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text("Value ₹" + Fmt.inr(s.cv, 0), color = mh.muted, fontSize = 13.sp)
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
            Text("Stock", color = mh.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("• " + Fmt.qty(s.cq) + " " + s.unit, color = if (s.cq < 0) mh.orange else mh.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Bar(cover, if (status.first == "Running low") Color(0xFFF59E0B) else mh.ink)
    }
}

@Composable
fun ItemScreen(vm: AppViewModel, nav: NavHostController, guid: String) {
    val p = vm.profile ?: return
    var period by rememberSaveable { mutableStateOf("fy") }
    val s = load(vm.version, vm.company, guid) { vm.repo.db.stock(vm.company) { false }.firstOrNull { it.guid == guid } }
    if (s == null) {
        Page("Stock item", vm, nav, back = true) { item { DCard { EmptyNote("Loading…") } } }
        return
    }
    val (from, to) = periodRange(period)
    val moves = load(vm.version, vm.company, guid, period) { vm.repo.db.movements(vm.company, s.name, from, to) } ?: emptyList()
    val opening = load(vm.version, vm.company, guid, period) { s.oq + vm.repo.db.qtyBefore(vm.company, s.name, from) } ?: s.oq
    val lastBuy = load(vm.version, vm.company, guid) { vm.repo.db.lastPrices(vm.company, true, null)[s.name] }
    Page(s.name, vm, nav, back = true, subtitle = s.parent + if (p.hidden(s.name)) " · hidden from lists" else "") {
        item {
            DCard {
                CardTitle("In stock (Tally)")
                Text(Fmt.qty(s.cq) + " " + s.unit, color = mh.ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    KeyValue("Value", "₹" + Fmt.inr(s.cv), Modifier.weight(1f))
                    KeyValue("MRP / Marginal", s.mrp?.let { "₹" + Fmt.inr(it) } ?: "Not set", Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    KeyValue("Last sale", s.lastRate?.let { "₹" + Fmt.inr(it) } ?: "–", Modifier.weight(1f))
                    KeyValue("Last purchase", lastBuy?.let { "₹" + Fmt.inr(it.rate) } ?: "–", Modifier.weight(1f))
                }
            }
        }
        item { PillRow(periods.filter { it.first != "last" }, period) { period = it } }
        item {
            var bal = opening
            DCard {
                Row(Modifier.fillMaxWidth()) {
                    Text("Opening quantity", color = mh.muted, modifier = Modifier.weight(1f))
                    Text(Fmt.qty(opening) + " " + s.unit, color = mh.muted)
                }
                if (moves.isEmpty()) EmptyNote("No movements in this period.")
                moves.forEach { m ->
                    bal += if (m.inward) m.qty else -m.qty
                    HorizontalDivider(color = mh.line)
                    Row(Modifier.fillMaxWidth().clickable { nav.open("voucher", m.guid) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(m.party.ifEmpty { m.type }, color = mh.ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(Fmt.date(m.date) + " · " + m.type + " " + m.no + " · ₹" + Fmt.inr(m.rate), color = mh.muted, fontSize = 12.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text((if (m.inward) "+" else "−") + Fmt.qty(m.qty), color = if (m.inward) mh.green else mh.ink, fontWeight = FontWeight.Bold)
                            Text(Fmt.qty(bal) + " " + s.unit, color = mh.muted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

// ---------- Website vouchers ----------

@Composable
fun WebVouchersScreen(vm: AppViewModel, nav: NavHostController) {
    val list = load(vm.version, vm.company) { vm.repo.db.webVouchers(vm.company) } ?: emptyList()
    Page("Website vouchers", vm, nav, back = true) {
        if (list.isEmpty()) item { DCard { EmptyNote("No vouchers made on the website or this app yet.") } }
        items(list, key = { it.id }) { w ->
            val (label, bg, fg) = when (w.status) {
                "posted" -> Triple("In Tally", mh.greenBg, mh.green)
                "failed" -> Triple("Rejected by Tally", mh.orangeBg, mh.orange)
                "needs_check" -> Triple("Check in Tally", mh.orangeBg, mh.orange)
                "claimed" -> Triple("Posting now", mh.panel2, mh.accent)
                else -> Triple("Waiting for sync", mh.panel2, mh.muted)
            }
            DCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(w.party, color = mh.ink, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("WEB-" + w.id + " · " + w.type + " · " + Fmt.date(w.date) + if (w.by.isNotEmpty()) " · " + w.by else "", color = mh.muted, fontSize = 12.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("₹" + Fmt.inr(w.total), color = mh.ink, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(4.dp))
                        StatusTag(label, bg, fg)
                    }
                }
                if (w.error.isNotBlank()) Text(w.error, color = mh.orange, fontSize = 13.sp)
            }
        }
    }
}
