package com.thepagination.mhanalyst.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

/** Indian number formatting (12,34,567.89), Dr/Cr balances and short dates. */
object Fmt {
    private val dateFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    private val shortDate = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

    fun inr(n: Double, decimals: Int = 2): String {
        val neg = n < 0
        val s = String.format(Locale.US, "%.${decimals}f", abs(n))
        val parts = s.split('.')
        var whole = parts[0]
        if (whole.length > 3) {
            val last3 = whole.takeLast(3)
            var rest = whole.dropLast(3)
            val sb = StringBuilder()
            while (rest.length > 2) {
                sb.insert(0, "," + rest.takeLast(2))
                rest = rest.dropLast(2)
            }
            whole = rest + sb.toString() + "," + last3
        }
        return (if (neg) "-" else "") + whole + if (decimals > 0) "." + parts[1] else ""
    }

    fun rupee(n: Double, decimals: Int = 2) = "₹" + inr(n, decimals)

    fun drcr(n: Double): String {
        if (abs(n) < 0.005) return "0.00"
        return inr(abs(n)) + if (n < 0) " Dr" else " Cr"
    }

    /** 12.5 L, 3.2 Cr, 45k: for chart axes. */
    fun short(n: Double): String {
        val a = abs(n)
        return when {
            a >= 1e7 -> String.format(Locale.US, if (a >= 1e8) "%.0f Cr" else "%.1f Cr", n / 1e7)
            a >= 1e5 -> String.format(Locale.US, if (a >= 1e6) "%.0f L" else "%.1f L", n / 1e5)
            a >= 1e3 -> String.format(Locale.US, "%.0fk", n / 1e3)
            else -> String.format(Locale.US, "%.0f", n)
        }
    }

    fun qty(n: Double): String {
        val s = String.format(Locale.US, "%.3f", n).trimEnd('0').trimEnd('.')
        return if (s == "-0") "0" else s
    }

    fun date(iso: String?): String = try { if (iso.isNullOrBlank()) "" else LocalDate.parse(iso.take(10)).format(dateFmt) } catch (e: Exception) { iso ?: "" }
    fun dayMonth(iso: String?): String = try { if (iso.isNullOrBlank()) "" else LocalDate.parse(iso.take(10)).format(shortDate) } catch (e: Exception) { iso ?: "" }

    /** Splits 1234.56 into "1,234" and ".56" so the paise can be shown lighter. */
    fun split(n: Double): Pair<String, String> {
        val s = inr(n, 2)
        val i = s.lastIndexOf('.')
        return if (i < 0) s to "" else s.substring(0, i) to s.substring(i)
    }

    fun r2(n: Double): Double = (if (n < 0) -1 else 1) * round(abs(n) * 100 + 1e-7) / 100
}

/** Forgiving search, same rules as the website: any word order, spaces optional, small typos allowed. */
object Fuzzy {
    fun tokens(s: String): List<String> = s.lowercase()
        .replace(Regex("([a-z])([0-9])"), "$1 $2")
        .replace(Regex("([0-9])([a-z])"), "$1 $2")
        .replace(Regex("[^a-z0-9.]+"), " ")
        .replace(Regex("(^|[^0-9])\\.|\\.(?![0-9])"), "$1 ")
        .split(' ').filter { it.isNotEmpty() }

    private fun distance(a: String, b: String, max: Int): Int {
        if (abs(a.length - b.length) > max) return max + 1
        var prev2 = IntArray(b.length + 1)
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            var best = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var v = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = minOf(v, prev2[j - 2] + 1)
                cur[j] = v
                if (v < best) best = v
            }
            if (best > max) return max + 1
            prev2 = prev
            prev = cur
        }
        return prev[b.length]
    }

    fun score(query: String, text: String): Int {
        val q = tokens(query)
        if (q.isEmpty()) return 1
        val words = tokens(text)
        val compact = words.joinToString("")
        var score = 0
        for (t in q) {
            if (compact.contains(t)) {
                score += 3
                if (words.any { it.startsWith(t) }) score += 2
                continue
            }
            if (t.length < 3) return 0
            val allow = if (t.length >= 7) 2 else 1
            val hit = words.any { w -> distance(t, w, allow) <= allow || (w.length > t.length && distance(t, w.take(t.length), allow) <= allow) }
            if (!hit) return 0
            score += 1
        }
        if (words.isNotEmpty() && words[0].startsWith(q[0])) score += 2
        return score
    }

    fun <T> filter(list: List<T>, query: String, text: (T) -> String): List<T> {
        if (query.isBlank()) return list
        return list.map { it to score(query, text(it)) }.filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<T, Int>> { it.second }.thenBy { text(it.first).length })
            .map { it.first }
    }
}
