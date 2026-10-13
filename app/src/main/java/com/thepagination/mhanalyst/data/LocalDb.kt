package com.thepagination.mhanalyst.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteStatement
import org.json.JSONObject
import java.time.LocalDate

data class Ledger(
    val guid: String, val name: String, val parent: String, val ob: Double, val cb: Double,
    val gstin: String, val mobile: String, val email: String, val address: String, val state: String,
)

data class StockItem(
    val guid: String, val name: String, val parent: String, val category: String, val unit: String,
    val oq: Double, val cq: Double, val cv: Double, val cr: Double, val mrp: Double?, val mrpUnit: String,
    var lastRate: Double? = null, var lastDisc: Double = 0.0, var lastDate: String? = null, var lastParty: String? = null,
    var sold30: Double = 0.0, var sold90: Double = 0.0,
)

data class VoucherRow(
    val guid: String, val date: String, val type: String, val no: String, val party: String, val amount: Double,
    val cancelled: Boolean, val optional: Boolean, val narration: String, val reference: String,
)

data class Line(val ledger: String, val amount: Double, val fromItem: Boolean, val bills: String)
data class InvLine(val item: String, val qty: Double, val unit: String, val rate: Double, val disc: Double, val amount: Double, val inward: Boolean, val godown: String)
data class WebVoucher(val id: Int, val type: String, val date: String, val party: String, val total: Double, val status: String, val error: String, val by: String, val at: String)
data class LedgerEntry(val guid: String, val date: String, val type: String, val no: String, val particulars: String, val amount: Double)
data class Movement(val guid: String, val date: String, val type: String, val no: String, val party: String, val inward: Boolean, val qty: Double, val rate: Double, val amount: Double)
data class Ranked(val name: String, val guid: String?, val amount: Double, val count: Double, val unit: String = "")
data class LastPrice(val rate: Double, val disc: Double, val date: String, val party: String)

data class Dashboard(
    val sales: Double, val salesPrev: Double, val purchases: Double, val purchasesPrev: Double, val salesCount: Int,
    val labels: List<String>, val salesSeries: List<Double>, val purchaseSeries: List<Double>,
    val dayLabels: List<String>, val dayValues: List<Double>,
    val receivable: Double, val payable: Double, val debtors: Int,
    val stockValue: Double, val itemsInStock: Int,
    val groups: List<Pair<String, Double>>, val topItems: List<Ranked>, val customers: List<Ranked>,
    val recent: List<VoucherRow>,
)

private const val SALE = "v.type LIKE '%sale%' AND v.type NOT LIKE '%return%'"
private const val PURCHASE = "v.type LIKE '%purchase%' AND v.type NOT LIKE '%return%'"
private const val LIVE = "v.cancelled = 0 AND v.optional = 0"

/** The phone's own copy of each company's Tally data. Every screen reads from here, so it opens instantly. */
class LocalDb(context: Context) : SQLiteOpenHelper(context, "mh.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        listOf(
            "CREATE TABLE ledgers (company TEXT, guid TEXT, name TEXT, parent TEXT, ob REAL, cb REAL, gstin TEXT, mobile TEXT, email TEXT, address TEXT, state TEXT, PRIMARY KEY (company, guid))",
            "CREATE INDEX ix_ledgers_name ON ledgers (company, name)",
            "CREATE TABLE items (company TEXT, guid TEXT, name TEXT, parent TEXT, category TEXT, unit TEXT, oq REAL, cq REAL, cv REAL, cr REAL, mrp REAL, mu TEXT, PRIMARY KEY (company, guid))",
            "CREATE TABLE vouchers (company TEXT, guid TEXT, mid INTEGER, date TEXT, type TEXT, no TEXT, party TEXT, ref TEXT, nar TEXT, amt REAL, cancelled INTEGER, optional INTEGER, PRIMARY KEY (company, guid))",
            "CREATE INDEX ix_vouchers_date ON vouchers (company, date)",
            "CREATE INDEX ix_vouchers_party ON vouchers (company, party)",
            "CREATE TABLE vlines (company TEXT, vguid TEXT, line INTEGER, ledger TEXT, amount REAL, src INTEGER, bills TEXT)",
            "CREATE INDEX ix_vlines_v ON vlines (company, vguid)",
            "CREATE INDEX ix_vlines_l ON vlines (company, ledger)",
            "CREATE TABLE vinv (company TEXT, vguid TEXT, line INTEGER, item TEXT, qty REAL, unit TEXT, rate REAL, disc REAL, amount REAL, inward INTEGER, godown TEXT)",
            "CREATE INDEX ix_vinv_v ON vinv (company, vguid)",
            "CREATE INDEX ix_vinv_i ON vinv (company, item)",
            "CREATE TABLE web (company TEXT, id INTEGER, type TEXT, date TEXT, party TEXT, total REAL, status TEXT, err TEXT, byuser TEXT, at TEXT, PRIMARY KEY (company, id))",
            "CREATE TABLE meta (company TEXT, k TEXT, v TEXT, PRIMARY KEY (company, k))",
        ).forEach { db.execSQL(it) }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        listOf("ledgers", "items", "vouchers", "vlines", "vinv", "web", "meta").forEach { db.execSQL("DROP TABLE IF EXISTS $it") }
        onCreate(db)
    }

    fun wipeAll() {
        val db = writableDatabase
        listOf("ledgers", "items", "vouchers", "vlines", "vinv", "web", "meta").forEach { db.delete(it, null, null) }
    }

    // ---------- applying a download ----------

    private fun SQLiteStatement.put(vararg v: Any?) {
        clearBindings()
        v.forEachIndexed { i, x ->
            val n = i + 1
            when (x) {
                null -> bindNull(n)
                is String -> bindString(n, x)
                is Int -> bindLong(n, x.toLong())
                is Long -> bindLong(n, x)
                is Double -> bindDouble(n, x)
                is Boolean -> bindLong(n, if (x) 1L else 0L)
                else -> bindString(n, x.toString())
            }
        }
        executeInsert()
    }

    fun apply(company: String, j: JSONObject, wipe: Boolean) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val c = arrayOf(company)
            if (wipe) listOf("ledgers", "items", "vouchers", "vlines", "vinv").forEach { db.delete(it, "company = ?", c) }

            j.optJSONArray("ledgers")?.let { arr ->
                val st = db.compileStatement("INSERT OR REPLACE INTO ledgers (company, guid, name, parent, ob, cb, gstin, mobile, email, address, state) VALUES (?,?,?,?,?,?,?,?,?,?,?)")
                for (k in 0 until arr.length()) {
                    val o = arr.getJSONObject(k)
                    st.put(company, o.optString("g"), o.optString("n"), o.optString("p"), o.optDouble("ob", 0.0), o.optDouble("cb", 0.0),
                        o.optString("gst"), o.optString("mob"), o.optString("em"), o.optString("addr"), o.optString("st"))
                }
            }
            j.optJSONArray("items")?.let { arr ->
                val st = db.compileStatement("INSERT OR REPLACE INTO items (company, guid, name, parent, category, unit, oq, cq, cv, cr, mrp, mu) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)")
                for (k in 0 until arr.length()) {
                    val o = arr.getJSONObject(k)
                    st.put(company, o.optString("g"), o.optString("n"), o.optString("p"), o.optString("cat"), o.optString("u"), o.optDouble("oq", 0.0),
                        o.optDouble("cq", 0.0), o.optDouble("cv", 0.0), o.optDouble("cr", 0.0), if (o.isNull("mrp")) null else o.optDouble("mrp"), o.optString("mu"))
                }
            }
            j.optJSONArray("deleted")?.let { arr ->
                for (k in 0 until arr.length()) {
                    val o = arr.getJSONObject(k)
                    val g = arrayOf(company, o.optString("g"))
                    when (o.optString("k")) {
                        "voucher" -> {
                            db.delete("vouchers", "company = ? AND guid = ?", g)
                            db.delete("vlines", "company = ? AND vguid = ?", g)
                            db.delete("vinv", "company = ? AND vguid = ?", g)
                        }
                        "ledger" -> db.delete("ledgers", "company = ? AND guid = ?", g)
                        "item" -> db.delete("items", "company = ? AND guid = ?", g)
                    }
                }
            }
            j.optJSONArray("vouchers")?.let { arr ->
                val sv = db.compileStatement("INSERT OR REPLACE INTO vouchers (company, guid, mid, date, type, no, party, ref, nar, amt, cancelled, optional) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)")
                val sl = db.compileStatement("INSERT INTO vlines (company, vguid, line, ledger, amount, src, bills) VALUES (?,?,?,?,?,?,?)")
                val si = db.compileStatement("INSERT INTO vinv (company, vguid, line, item, qty, unit, rate, disc, amount, inward, godown) VALUES (?,?,?,?,?,?,?,?,?,?,?)")
                for (k in 0 until arr.length()) {
                    val o = arr.getJSONObject(k)
                    val g = o.optString("g")
                    if (!wipe) {
                        db.delete("vlines", "company = ? AND vguid = ?", arrayOf(company, g))
                        db.delete("vinv", "company = ? AND vguid = ?", arrayOf(company, g))
                    }
                    sv.put(company, g, o.optInt("mid"), o.optString("d"), o.optString("t"), o.optString("no"), o.optString("party"), o.optString("ref"),
                        o.optString("nar"), o.optDouble("amt", 0.0), o.optInt("can"), o.optInt("opt"))
                    o.optJSONArray("L")?.let { ls ->
                        for (n in 0 until ls.length()) {
                            val l = ls.getJSONArray(n)
                            sl.put(company, g, n, l.optString(0), l.optDouble(1, 0.0), l.optInt(2), l.optString(3))
                        }
                    }
                    o.optJSONArray("I")?.let { ls ->
                        for (n in 0 until ls.length()) {
                            val l = ls.getJSONArray(n)
                            si.put(company, g, n, l.optString(0), l.optDouble(1, 0.0), l.optString(2), l.optDouble(3, 0.0), l.optDouble(4, 0.0),
                                l.optDouble(5, 0.0), l.optInt(6), l.optString(7))
                        }
                    }
                }
            }
            j.optJSONArray("web")?.let { arr ->
                db.delete("web", "company = ?", c)
                val st = db.compileStatement("INSERT OR REPLACE INTO web (company, id, type, date, party, total, status, err, byuser, at) VALUES (?,?,?,?,?,?,?,?,?,?)")
                for (k in 0 until arr.length()) {
                    val o = arr.getJSONObject(k)
                    st.put(company, o.optInt("id"), o.optString("t"), o.optString("d"), o.optString("party"), o.optDouble("total", 0.0),
                        o.optString("status"), o.optString("err"), o.optString("by"), o.optString("at"))
                }
            }
            j.optJSONObject("agent")?.let { a ->
                val st = db.compileStatement("INSERT OR REPLACE INTO meta (company, k, v) VALUES (?,?,?)")
                st.put(company, "agent_online", a.optBoolean("online").toString())
                st.put(company, "agent_last_sync", if (a.isNull("last_sync")) "" else a.optString("last_sync"))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // ---------- reading ----------

    private fun <T> q(sql: String, vararg args: String, map: (Cursor) -> T): List<T> {
        val out = ArrayList<T>()
        readableDatabase.rawQuery(sql, arrayOf(*args)).use { c -> while (c.moveToNext()) out.add(map(c)) }
        return out
    }

    private fun one(sql: String, vararg args: String): Double = q(sql, *args) { if (it.isNull(0)) 0.0 else it.getDouble(0) }.firstOrNull() ?: 0.0
    private fun Cursor.s(i: Int): String = getString(i) ?: ""

    fun meta(company: String, key: String): String? = q("SELECT v FROM meta WHERE company = ? AND k = ?", company, key) { it.s(0) }.firstOrNull()
    fun hasData(company: String): Boolean = one("SELECT COUNT(*) FROM ledgers WHERE company = ?", company) > 0

    private val voucherCols = "v.guid, v.date, v.type, v.no, v.party, v.amt, v.cancelled, v.optional, v.nar, v.ref"
    private fun voucherRow(c: Cursor) = VoucherRow(c.s(0), c.s(1), c.s(2), c.s(3), c.s(4), c.getDouble(5), c.getInt(6) == 1, c.getInt(7) == 1, c.s(8), c.s(9))

    fun dashboard(company: String, range: String, hidden: (String) -> Boolean): Dashboard {
        val today = LocalDate.now()
        val from: LocalDate
        val prevFrom: LocalDate
        val prevTo: LocalDate
        if (range == "month") {
            from = today.withDayOfMonth(1)
            prevFrom = from.minusMonths(1)
            val sameDay = prevFrom.plusDays((today.dayOfMonth - 1).toLong())
            prevTo = if (sameDay.isAfter(from.minusDays(1))) from.minusDays(1) else sameDay
        } else {
            val n = if (range == "6m") 6L else 12L
            from = today.withDayOfMonth(1).minusMonths(n - 1)
            prevFrom = from.minusMonths(n)
            prevTo = from.minusDays(1)
        }
        val f = from.toString()
        val t = today.toString()
        val sum = { cond: String, a: String, b: String -> one("SELECT SUM(v.amt) FROM vouchers v WHERE v.company = ? AND $LIVE AND $cond AND v.date BETWEEN ? AND ?", company, a, b) }

        val labels = ArrayList<String>()
        val keys = ArrayList<String>()
        if (range == "month") {
            var d = from
            while (!d.isAfter(today)) { keys.add(d.toString()); labels.add(d.dayOfMonth.toString()); d = d.plusDays(1) }
        } else {
            var m = from
            while (!m.isAfter(today)) {
                keys.add(m.toString().take(7))
                labels.add(m.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() })
                m = m.plusMonths(1)
            }
        }
        val keyExpr = if (range == "month") "v.date" else "substr(v.date, 1, 7)"
        val sales = HashMap<String, Double>()
        val purch = HashMap<String, Double>()
        q("SELECT $keyExpr, SUM(CASE WHEN $SALE THEN v.amt ELSE 0 END), SUM(CASE WHEN $PURCHASE THEN v.amt ELSE 0 END) FROM vouchers v WHERE v.company = ? AND $LIVE AND v.date BETWEEN ? AND ? GROUP BY 1", company, f, t) {
            sales[it.s(0)] = it.getDouble(1); purch[it.s(0)] = it.getDouble(2)
        }

        val dayKeys = (13 downTo 0).map { today.minusDays(it.toLong()) }
        val dayMap = HashMap<String, Double>()
        q("SELECT v.date, SUM(v.amt) FROM vouchers v WHERE v.company = ? AND $LIVE AND $SALE AND v.date >= ? GROUP BY v.date", company, dayKeys.first().toString()) { dayMap[it.s(0)] = it.getDouble(1) }

        var stockValue = 0.0
        var inStock = 0
        q("SELECT name, cv, cq FROM items WHERE company = ?", company) {
            if (!hidden(it.s(0))) { stockValue += it.getDouble(1); if (it.getDouble(2) > 0) inStock++ }
        }

        val itemGroup = HashMap<String, Pair<String, String>>()
        q("SELECT name, parent, guid FROM items WHERE company = ?", company) { itemGroup[it.s(0)] = (it.s(1).ifEmpty { "Other" }) to it.s(2) }
        val itemSales = q("SELECT i.item, SUM(ABS(i.amount)), SUM(ABS(i.qty)), MAX(i.unit) FROM vinv i JOIN vouchers v ON v.company = i.company AND v.guid = i.vguid " +
            "WHERE i.company = ? AND $LIVE AND $SALE AND i.inward = 0 AND v.date BETWEEN ? AND ? GROUP BY i.item ORDER BY 2 DESC LIMIT 80", company, f, t) {
            Ranked(it.s(0), itemGroup[it.s(0)]?.second, it.getDouble(1), it.getDouble(2), it.s(3))
        }.filter { !hidden(it.name) }
        val groupTotals = LinkedHashMap<String, Double>()
        itemSales.forEach { val g = itemGroup[it.name]?.first ?: "Other"; groupTotals[g] = (groupTotals[g] ?: 0.0) + it.amount }
        var groups = groupTotals.entries.sortedByDescending { it.value }.map { it.key to it.value }
        if (groups.size > 5) groups = groups.take(5) + ("Others" to groups.drop(5).sumOf { it.second })

        val ledgerGuid = HashMap<String, String>()
        val customers = q("SELECT v.party, SUM(v.amt), COUNT(*) FROM vouchers v WHERE v.company = ? AND $LIVE AND $SALE AND v.date BETWEEN ? AND ? AND v.party <> '' GROUP BY v.party ORDER BY 2 DESC LIMIT 5", company, f, t) {
            Ranked(it.s(0), null, it.getDouble(1), it.getDouble(2))
        }
        customers.forEach { r -> q("SELECT guid FROM ledgers WHERE company = ? AND name = ?", company, r.name) { it.s(0) }.firstOrNull()?.let { ledgerGuid[r.name] = it } }

        return Dashboard(
            sales = sum(SALE, f, t), salesPrev = sum(SALE, prevFrom.toString(), prevTo.toString()),
            purchases = sum(PURCHASE, f, t), purchasesPrev = sum(PURCHASE, prevFrom.toString(), prevTo.toString()),
            salesCount = one("SELECT COUNT(*) FROM vouchers v WHERE v.company = ? AND $LIVE AND $SALE AND v.date BETWEEN ? AND ?", company, f, t).toInt(),
            labels = labels, salesSeries = keys.map { sales[it] ?: 0.0 }, purchaseSeries = keys.map { purch[it] ?: 0.0 },
            dayLabels = dayKeys.map { it.dayOfWeek.name.take(2).lowercase().replaceFirstChar { c -> c.uppercase() } },
            dayValues = dayKeys.map { dayMap[it.toString()] ?: 0.0 },
            receivable = -one("SELECT SUM(cb) FROM ledgers WHERE company = ? AND parent LIKE '%debtor%' AND cb < 0", company),
            payable = one("SELECT SUM(cb) FROM ledgers WHERE company = ? AND parent LIKE '%creditor%' AND cb > 0", company),
            debtors = one("SELECT COUNT(*) FROM ledgers WHERE company = ? AND parent LIKE '%debtor%' AND cb < 0", company).toInt(),
            stockValue = stockValue, itemsInStock = inStock,
            groups = groups, topItems = itemSales.take(5),
            customers = customers.map { it.copy(guid = ledgerGuid[it.name]) },
            recent = q("SELECT $voucherCols FROM vouchers v WHERE v.company = ? ORDER BY v.date DESC, v.mid DESC LIMIT 6", company) { voucherRow(it) },
        )
    }

    fun vouchers(company: String, from: String, to: String, type: String?): List<VoucherRow> =
        if (type.isNullOrEmpty()) q("SELECT $voucherCols FROM vouchers v WHERE v.company = ? AND v.date BETWEEN ? AND ? ORDER BY v.date DESC, v.mid DESC LIMIT 3000", company, from, to) { voucherRow(it) }
        else q("SELECT $voucherCols FROM vouchers v WHERE v.company = ? AND v.date BETWEEN ? AND ? AND v.type = ? ORDER BY v.date DESC, v.mid DESC LIMIT 3000", company, from, to, type) { voucherRow(it) }

    fun dayBook(company: String, date: String): List<VoucherRow> =
        q("SELECT $voucherCols FROM vouchers v WHERE v.company = ? AND v.date = ? ORDER BY v.mid", company, date) { voucherRow(it) }

    fun lastVoucherDate(company: String, onOrBefore: String): String? =
        q("SELECT MAX(date) FROM vouchers WHERE company = ? AND date <= ?", company, onOrBefore) { it.getString(0) }.firstOrNull()

    fun voucherTypes(company: String): List<String> = q("SELECT DISTINCT type FROM vouchers WHERE company = ? ORDER BY type", company) { it.s(0) }

    fun voucher(company: String, guid: String): Triple<VoucherRow, List<Line>, List<InvLine>>? {
        val v = q("SELECT $voucherCols FROM vouchers v WHERE v.company = ? AND v.guid = ?", company, guid) { voucherRow(it) }.firstOrNull() ?: return null
        val lines = q("SELECT ledger, SUM(amount), MAX(src), MAX(bills) FROM vlines WHERE company = ? AND vguid = ? GROUP BY ledger ORDER BY MIN(line)", company, guid) {
            Line(it.s(0), it.getDouble(1), it.getInt(2) == 1, it.s(3))
        }
        val inv = q("SELECT item, qty, unit, rate, disc, amount, inward, godown FROM vinv WHERE company = ? AND vguid = ? ORDER BY line", company, guid) {
            InvLine(it.s(0), it.getDouble(1), it.s(2), it.getDouble(3), it.getDouble(4), it.getDouble(5), it.getInt(6) == 1, it.s(7))
        }
        return Triple(v, lines, inv)
    }

    fun ledgers(company: String): List<Ledger> = q("SELECT guid, name, parent, ob, cb, gstin, mobile, email, address, state FROM ledgers WHERE company = ? ORDER BY name", company) {
        Ledger(it.s(0), it.s(1), it.s(2), it.getDouble(3), it.getDouble(4), it.s(5), it.s(6), it.s(7), it.s(8), it.s(9))
    }

    fun ledgerByGuid(company: String, guid: String): Ledger? = q("SELECT guid, name, parent, ob, cb, gstin, mobile, email, address, state FROM ledgers WHERE company = ? AND guid = ?", company, guid) {
        Ledger(it.s(0), it.s(1), it.s(2), it.getDouble(3), it.getDouble(4), it.s(5), it.s(6), it.s(7), it.s(8), it.s(9))
    }.firstOrNull()

    fun ledgerGuid(company: String, name: String): String? = q("SELECT guid FROM ledgers WHERE company = ? AND name = ?", company, name) { it.s(0) }.firstOrNull()
    fun itemGuid(company: String, name: String): String? = q("SELECT guid FROM items WHERE company = ? AND name = ?", company, name) { it.s(0) }.firstOrNull()

    /** Opening balance for the period and every voucher touching the ledger, with "particulars" like Tally. */
    fun ledgerEntries(company: String, name: String, ob: Double, from: String, to: String): Pair<Double, List<LedgerEntry>> {
        val before = one("SELECT SUM(l.amount) FROM vlines l JOIN vouchers v ON v.company = l.company AND v.guid = l.vguid WHERE l.company = ? AND l.ledger = ? AND v.date < ? AND $LIVE", company, name, from)
        val rows = q("SELECT v.guid, v.date, v.type, v.no, v.party, SUM(l.amount) FROM vlines l JOIN vouchers v ON v.company = l.company AND v.guid = l.vguid " +
            "WHERE l.company = ? AND l.ledger = ? AND v.date BETWEEN ? AND ? AND $LIVE GROUP BY v.guid ORDER BY v.date, v.mid LIMIT 3000", company, name, from, to) {
            LedgerEntry(it.s(0), it.s(1), it.s(2), it.s(3), it.s(4), it.getDouble(5))
        }
        val result = rows.map { e ->
            val others = q("SELECT ledger, SUM(amount) FROM vlines WHERE company = ? AND vguid = ? AND ledger <> ? GROUP BY ledger", company, e.guid, name) { it.s(0) to it.getDouble(1) }
                .filter { if (e.amount < 0) it.second > 0 else it.second < 0 }
                .sortedByDescending { kotlin.math.abs(it.second) }
            val p = when {
                others.isEmpty() -> e.particulars
                others.size == 1 -> others[0].first
                else -> others[0].first + " + " + (others.size - 1) + " more"
            }
            e.copy(particulars = p)
        }
        return (ob + before) to result
    }

    /** All visible stock items with last sold rate and units sold in 30 / 90 days. */
    fun stock(company: String, hidden: (String) -> Boolean): List<StockItem> {
        val items = q("SELECT guid, name, parent, category, unit, oq, cq, cv, cr, mrp, mu FROM items WHERE company = ? ORDER BY name", company) {
            StockItem(it.s(0), it.s(1), it.s(2), it.s(3), it.s(4), it.getDouble(5), it.getDouble(6), it.getDouble(7), it.getDouble(8),
                if (it.isNull(9)) null else it.getDouble(9), it.s(10))
        }.filter { !hidden(it.name) }
        val byName = items.associateBy { it.name }
        val d30 = LocalDate.now().minusDays(30).toString()
        val d90 = LocalDate.now().minusDays(90).toString()
        q("SELECT i.item, i.rate, i.disc, v.date, v.party, ABS(i.qty) FROM vinv i JOIN vouchers v ON v.company = i.company AND v.guid = i.vguid " +
            "WHERE i.company = ? AND i.inward = 0 AND $SALE AND $LIVE AND v.date >= ? ORDER BY v.date DESC, v.mid DESC", company, LocalDate.now().minusMonths(24).toString()) { c ->
            val item = byName[c.s(0)]
            if (item != null) {
                if (item.lastRate == null && c.getDouble(1) > 0) {
                    item.lastRate = c.getDouble(1); item.lastDisc = c.getDouble(2); item.lastDate = c.s(3); item.lastParty = c.s(4)
                }
                val d = c.s(3)
                if (d >= d90) item.sold90 += c.getDouble(5)
                if (d >= d30) item.sold30 += c.getDouble(5)
            }
        }
        return items
    }

    fun movements(company: String, item: String, from: String, to: String): List<Movement> =
        q("SELECT v.guid, v.date, v.type, v.no, v.party, i.inward, ABS(i.qty), i.rate, ABS(i.amount) FROM vinv i JOIN vouchers v ON v.company = i.company AND v.guid = i.vguid " +
            "WHERE i.company = ? AND i.item = ? AND v.date BETWEEN ? AND ? AND $LIVE ORDER BY v.date, v.mid, i.line LIMIT 3000", company, item, from, to) {
            Movement(it.s(0), it.s(1), it.s(2), it.s(3), it.s(4), it.getInt(5) == 1, it.getDouble(6), it.getDouble(7), it.getDouble(8))
        }

    fun qtyBefore(company: String, item: String, from: String): Double =
        one("SELECT SUM(CASE WHEN i.inward = 1 THEN ABS(i.qty) ELSE -ABS(i.qty) END) FROM vinv i JOIN vouchers v ON v.company = i.company AND v.guid = i.vguid " +
            "WHERE i.company = ? AND i.item = ? AND v.date < ? AND $LIVE", company, item, from)

    /** Last price for each item: for one party, or for anyone. Sales are outward, purchases inward. */
    fun lastPrices(company: String, purchase: Boolean, party: String?): Map<String, LastPrice> {
        val out = HashMap<String, LastPrice>()
        val cond = if (purchase) "i.inward = 1 AND $PURCHASE" else "i.inward = 0 AND $SALE"
        val sql = "SELECT i.item, i.rate, i.disc, v.date, v.party FROM vinv i JOIN vouchers v ON v.company = i.company AND v.guid = i.vguid " +
            "WHERE i.company = ? AND $cond AND $LIVE AND i.rate > 0" + (if (party != null) " AND v.party = ?" else "") + " ORDER BY v.date DESC, v.mid DESC LIMIT 20000"
        val args = if (party != null) arrayOf(company, party) else arrayOf(company)
        q(sql, *args) { if (!out.containsKey(it.s(0))) out[it.s(0)] = LastPrice(it.getDouble(1), it.getDouble(2), it.s(3), it.s(4)) }
        return out
    }

    fun webVouchers(company: String): List<WebVoucher> = q("SELECT id, type, date, party, total, status, err, byuser, at FROM web WHERE company = ? ORDER BY id DESC", company) {
        WebVoucher(it.getInt(0), it.s(1), it.s(2), it.s(3), it.getDouble(4), it.s(5), it.s(6), it.s(7), it.s(8))
    }
}
