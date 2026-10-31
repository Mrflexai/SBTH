package ma.remorquepro.core.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

object Heures {
    private val REGEX = Regex("""^([01]?\d|2[0-3])[:hH]([0-5]\d)$""")

    /** Normalise une saisie d'heure (« 8:5 » invalide, « 8:05 », « 08h05 ») vers « HH:mm », sinon null. */
    fun normaliser(saisie: String?): String? {
        val s = saisie?.trim().orEmpty()
        val m = REGEX.matchEntire(s) ?: return null
        return String.format(Locale.ROOT, "%02d:%s", m.groupValues[1].toInt(), m.groupValues[2])
    }

    fun estValide(saisie: String?): Boolean = normaliser(saisie) != null

    fun parse(s: String?): LocalTime? = normaliser(s)?.let { LocalTime.parse(it) }

    fun format(t: LocalTime): String = String.format(Locale.ROOT, "%02d:%02d", t.hour, t.minute)
}

object Supplements {
    /**
     * Vrai si l'heure est dans la plage de nuit configurée. La plage peut chevaucher minuit
     * (ex. 20:00 → 06:00) : début inclus, fin exclue.
     */
    fun estNuit(heure: String?, debut: String, fin: String): Boolean {
        val h = Heures.parse(heure) ?: return false
        val d = Heures.parse(debut) ?: return false
        val f = Heures.parse(fin) ?: return false
        if (d == f) return false
        return if (d < f) h >= d && h < f else h >= d || h < f
    }

    fun estWeekend(date: LocalDate, samediEstWeekend: Boolean): Boolean = when (date.dayOfWeek) {
        DayOfWeek.SUNDAY -> true
        DayOfWeek.SATURDAY -> samediEstWeekend
        else -> false
    }
}

object Formats {
    val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val MOIS = listOf(
        "Janvier", "Février", "Mars", "Avril", "Mai", "Juin",
        "Juillet", "Août", "Septembre", "Octobre", "Novembre", "Décembre",
    )
    private val MOIS_COURT = listOf(
        "janv.", "févr.", "mars", "avr.", "mai", "juin",
        "juil.", "août", "sept.", "oct.", "nov.", "déc.",
    )

    fun date(d: LocalDate?): String = d?.format(DATE) ?: ""

    fun mois(ym: YearMonth): String = "${MOIS[ym.monthValue - 1]} ${ym.year}"

    fun moisCourt(ym: YearMonth): String = MOIS_COURT[ym.monthValue - 1]

    /** Montant au format marocain/français : « 1 234,50 ». */
    fun montant(v: Double): String {
        val negatif = v < 0
        val centimes = Math.round(kotlin.math.abs(arrondi2(v)) * 100)
        val entier = (centimes / 100).toString()
        val dec = (centimes % 100).toString().padStart(2, '0')
        return (if (negatif) "-" else "") + groupe(entier) + "," + dec
    }

    fun montantDh(v: Double): String = montant(v) + " DH"

    fun entier(v: Int): String = (if (v < 0) "-" else "") + groupe(kotlin.math.abs(v.toLong()).toString())

    fun km(v: Int): String = entier(v) + " km"

    private fun groupe(chiffres: String): String {
        val sb = StringBuilder()
        chiffres.forEachIndexed { i, c ->
            if (i > 0 && (chiffres.length - i) % 3 == 0) sb.append(' ')
            sb.append(c)
        }
        return sb.toString()
    }

    /** Analyse une saisie de montant : accepte « 1 234,5 », « 1234.50 », vide = 0. Null si invalide. */
    fun parseMontant(saisie: String): Double? {
        val s = saisie.trim().replace(" ", "").replace(" ", "").replace(" ", "").replace(',', '.')
        if (s.isEmpty()) return 0.0
        return s.toDoubleOrNull()?.takeIf { !it.isNaN() && !it.isInfinite() }
    }

    fun parseEntier(saisie: String): Int? {
        val s = saisie.trim().replace(" ", "").replace(" ", "").replace(" ", "")
        if (s.isEmpty()) return null
        return s.toIntOrNull()
    }
}

enum class TypePeriode(val libelle: String) {
    JOUR("Jour"),
    SEMAINE("Semaine"),
    MOIS("Mois"),
    PERSONNALISEE("Période personnalisée"),
    TOUT("Toutes les dates"),
}

/** Période de dates incluse aux deux bornes. */
data class Periode(val debut: LocalDate, val fin: LocalDate, val type: TypePeriode = TypePeriode.PERSONNALISEE) {
    init {
        require(!fin.isBefore(debut)) { "La date de fin doit être postérieure ou égale à la date de début" }
    }

    fun contient(d: LocalDate): Boolean = !d.isBefore(debut) && !d.isAfter(fin)

    /** Mois couverts par la période, dans l'ordre. */
    fun mois(): List<YearMonth> {
        val res = mutableListOf<YearMonth>()
        var ym = YearMonth.from(debut)
        val dernier = YearMonth.from(fin)
        while (!ym.isAfter(dernier)) {
            res += ym
            ym = ym.plusMonths(1)
        }
        return res
    }

    val libelle: String
        get() = when (type) {
            TypePeriode.JOUR -> "Journée du ${Formats.date(debut)}"
            TypePeriode.MOIS -> Formats.mois(YearMonth.from(debut))
            TypePeriode.TOUT -> "Toutes les dates"
            else -> "Du ${Formats.date(debut)} au ${Formats.date(fin)}"
        }

    /** Fragment utilisable dans un nom de fichier. */
    val suffixeFichier: String
        get() = when (type) {
            TypePeriode.JOUR -> debut.toString()
            TypePeriode.MOIS -> YearMonth.from(debut).toString()
            else -> "${debut}_au_$fin"
        }

    companion object {
        fun jour(d: LocalDate) = Periode(d, d, TypePeriode.JOUR)

        /** Semaine du lundi au dimanche contenant la date. */
        fun semaine(d: LocalDate) = Periode(
            d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
            d.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)),
            TypePeriode.SEMAINE,
        )

        fun mois(ym: YearMonth) = Periode(ym.atDay(1), ym.atEndOfMonth(), TypePeriode.MOIS)

        fun personnalisee(debut: LocalDate, fin: LocalDate) = Periode(debut, fin, TypePeriode.PERSONNALISEE)

        fun tout() = Periode(LocalDate.of(2000, 1, 1), LocalDate.of(2099, 12, 31), TypePeriode.TOUT)
    }
}
