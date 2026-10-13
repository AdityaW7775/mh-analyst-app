package com.thepagination.mhanalyst.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.thepagination.mhanalyst.data.AppViewModel
import com.thepagination.mhanalyst.data.VoucherRow
import com.thepagination.mhanalyst.util.Fmt
import kotlin.math.roundToInt

private fun pct(now: Double, before: Double): Int? = if (before > 0) ((now - before) / before * 100).roundToInt() else null

@Composable
fun HomeScreen(vm: AppViewModel, nav: NavHostController) {
    var range by rememberSaveable { mutableStateOf("month") }
    val p = vm.profile ?: return
    val d = load(vm.version, vm.company, range) { vm.repo.db.dashboard(vm.company, range) { p.hidden(it) } }
    val periodLabel = when (range) { "6m" -> "Last 6 months"; "12m" -> "Last 12 months"; else -> "This month" }
    val prevLabel = when (range) { "6m" -> "previous 6 months"; "12m" -> "previous 12 months"; else -> "same days last month" }
    val white = Color.White

    Page("Analytics", vm, nav) {
        item { Segmented(listOf("month" to "This month", "6m" to "6 months", "12m" to "12 months"), range, { range = it }, Modifier.fillMaxWidth()) }
        if (d == null) return@Page

        item {
            DCard(onClick = { nav.navigate("vouchers") }) {
                CardTitle("Total sales")
                BigAmount(d.sales, chip = pct(d.sales, d.salesPrev))
                Sub("₹" + Fmt.inr(d.salesPrev, 0) + " " + prevLabel + " · " + d.salesCount + " invoices")
                LineChart(d.salesSeries, d.labels, mh.accent, mh.muted, mh.line, Modifier.padding(top = 8.dp))
            }
        }
        item {
            DCard(background = mh.darkCard) {
                CardTitle("Purchases", color = white.copy(alpha = 0.62f), trailing = { Text(periodLabel, color = white.copy(alpha = 0.62f), fontSize = 13.sp) })
                BigAmount(d.purchases, color = white, faded = white.copy(alpha = 0.45f), chip = pct(d.purchases, d.purchasesPrev))
                Sub("₹" + Fmt.inr(d.purchasesPrev, 0) + " " + prevLabel, white.copy(alpha = 0.62f))
                LineChart(d.purchaseSeries, d.labels, white, white.copy(alpha = 0.55f), white.copy(alpha = 0.08f), Modifier.padding(top = 8.dp))
            }
        }
        item {
            DCard(background = mh.blueCard, onClick = { nav.navigate("ledgers") }) {
                CardTitle("To receive", color = white.copy(alpha = 0.8f))
                BigAmount(d.receivable, color = white, faded = white.copy(alpha = 0.6f))
                Sub("${d.debtors} customers owe you · ₹${Fmt.inr(d.payable, 0)} you owe suppliers", white.copy(alpha = 0.85f))
                val total = (d.receivable + d.payable).coerceAtLeast(1.0)
                val rp = (d.receivable / total * 100).roundToInt()
                Row(Modifier.fillMaxWidth().padding(top = 10.dp).height(54.dp).clip(RoundedCornerShape(12.dp))) {
                    Box(Modifier.weight(rp.coerceAtLeast(1).toFloat()).fillMaxHeight().background(white), contentAlignment = Alignment.BottomStart) {
                        Text("$rp%", color = mh.darkCard, fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp))
                    }
                    Box(Modifier.weight((100 - rp).coerceAtLeast(1).toFloat()).fillMaxHeight().background(mh.darkCard), contentAlignment = Alignment.BottomEnd) {
                        Text("${100 - rp}%", color = white, fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text("● To receive", color = white, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text("● To pay", color = white.copy(alpha = 0.85f), fontSize = 12.sp)
                }
            }
        }
        item {
            DCard(onClick = { nav.navigate("daybook") }) {
                CardTitle("Last 14 days")
                BigAmount(d.dayValues.sum(), size = 26)
                Sub("Best day ₹" + Fmt.inr(d.dayValues.maxOrNull() ?: 0.0, 0))
                BarChart(d.dayValues, d.dayLabels, mh.accent, mh.barMuted, mh.muted, Modifier.padding(top = 8.dp))
            }
        }
        if (d.groups.isNotEmpty()) item {
            val palette = listOf(mh.ink, mh.accent, Color(0xFF43B7E8), Color(0xFF9AA4B2), Color(0xFFC9CFD8), Color(0xFFE6E9EE))
            val sum = d.groups.sumOf { it.second }.coerceAtLeast(1.0)
            DCard(onClick = { nav.navigate("stock") }) {
                CardTitle("Top categories")
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Donut(d.groups.map { it.second }, palette, mh.panel) }
                d.groups.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        pair.forEach { (name, v) ->
                            val i = d.groups.indexOfFirst { it.first == name }
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                LegendDot(palette[i % palette.size])
                                Spacer(Modifier.width(6.dp))
                                Text(name, color = mh.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text("${(v / sum * 100).roundToInt()}%", color = mh.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            DCard {
                CardTitle("Stock")
                BigAmount(d.stockValue)
                Sub("${d.itemsInStock} items in stock, at Tally's closing rate")
                Text("Top products · $periodLabel", color = mh.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
                if (d.topItems.isEmpty()) EmptyNote("No sales in this period.")
                d.topItems.forEachIndexed { i, r ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(enabled = r.guid != null) { r.guid?.let { nav.open("item", it) } }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(mh.panel2), contentAlignment = Alignment.Center) {
                            Text("${i + 1}", color = mh.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.name, color = mh.ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(Fmt.qty(r.count) + " " + r.unit + " sold", color = mh.muted, fontSize = 12.sp)
                        }
                        Text("₹" + Fmt.inr(r.amount, 0), color = mh.ink, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item {
            DCard {
                CardTitle("Top customers", trailing = { Text(periodLabel, color = mh.muted, fontSize = 13.sp) })
                if (d.customers.isEmpty()) EmptyNote("No sales in this period.")
                val total = d.customers.sumOf { it.amount }.coerceAtLeast(1.0)
                d.customers.forEach { c ->
                    val share = (c.amount / total * 100).roundToInt()
                    Column(Modifier.fillMaxWidth().clickable(enabled = c.guid != null) { c.guid?.let { nav.open("ledger", it) } }.padding(vertical = 7.dp)) {
                        Row {
                            Text(c.name, color = mh.ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text("$share% · ${c.count.toInt()} invoices", color = mh.muted, fontSize = 12.sp)
                        }
                        Bar(share / 100f, mh.ink, Modifier.padding(top = 6.dp), track = mh.panel2)
                    }
                }
            }
        }
        item {
            DCard {
                CardTitle("Latest vouchers")
                d.recent.forEach { VoucherLine(it) { nav.open("voucher", it.guid) } }
            }
        }
    }
}

/** One voucher in a list: coloured letter, party, type and number, amount. */
@Composable
fun VoucherLine(v: VoucherRow, onClick: () -> Unit) {
    val purchase = v.type.contains("purchase", ignoreCase = true)
    val sale = v.type.contains("sale", ignoreCase = true)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(if (purchase) mh.orangeBg else if (sale) mh.greenBg else mh.panel2),
            contentAlignment = Alignment.Center,
        ) { Text(v.type.take(1).uppercase(), color = if (purchase) mh.orange else if (sale) mh.green else mh.muted, fontWeight = FontWeight.ExtraBold) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(v.party.ifEmpty { v.type }, color = if (v.cancelled) mh.muted else mh.ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(v.type + " " + v.no + " · " + Fmt.date(v.date) + if (v.cancelled) " · Cancelled" else "", color = mh.muted, fontSize = 12.sp, maxLines = 1)
        }
        Text("₹" + Fmt.inr(v.amount), color = mh.ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}
