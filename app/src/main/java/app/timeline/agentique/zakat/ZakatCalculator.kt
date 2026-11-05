package app.timeline.agentique.zakat

enum class NisabBasis { GOLD, SILVER }

data class ZakatInput(
    val cash: Double = 0.0,
    val goldGrams: Double = 0.0,
    val silverGrams: Double = 0.0,
    val investments: Double = 0.0,
    val receivables: Double = 0.0,
    val inventory: Double = 0.0,
    val debtsDue: Double = 0.0,
    val goldPrice: Double = 0.0,
    val silverPrice: Double = 0.0,
    val basis: NisabBasis? = null,
    val hawlComplete: Boolean = false,
)

enum class ZakatKey { CASH, GOLD, SILVER, INVESTMENTS, RECEIVABLES, INVENTORY, GROSS, DEBTS, NET, NISAB }
data class ZakatLine(val key: ZakatKey, val amount: Double)

data class ZakatResult(
    val lines: List<ZakatLine>,
    val net: Double,
    val nisab: Double,
    val reachedNisab: Boolean,
    val hawlComplete: Boolean,
    val due: Double,
)

/** Pure arithmetic, every step visible. The language model never computes zakat. */
object ZakatCalculator {
    const val GOLD_NISAB_GRAMS = 85.0
    const val SILVER_NISAB_GRAMS = 595.0
    const val RATE = 0.025

    /** Returns null while the input is incomplete (no basis chosen, or a needed price is missing). */
    fun calculate(i: ZakatInput): ZakatResult? {
        val basis = i.basis ?: return null
        val basisPrice = if (basis == NisabBasis.GOLD) i.goldPrice else i.silverPrice
        if (basisPrice <= 0.0) return null
        if (i.goldGrams > 0 && i.goldPrice <= 0.0) return null
        if (i.silverGrams > 0 && i.silverPrice <= 0.0) return null

        val gold = i.goldGrams * i.goldPrice
        val silver = i.silverGrams * i.silverPrice
        val gross = i.cash + gold + silver + i.investments + i.receivables + i.inventory
        val net = gross - i.debtsDue
        val nisab = basisPrice * if (basis == NisabBasis.GOLD) GOLD_NISAB_GRAMS else SILVER_NISAB_GRAMS
        val reached = net >= nisab
        val due = if (reached && i.hawlComplete) net * RATE else 0.0
        return ZakatResult(
            lines = listOf(
                ZakatLine(ZakatKey.CASH, i.cash),
                ZakatLine(ZakatKey.GOLD, gold),
                ZakatLine(ZakatKey.SILVER, silver),
                ZakatLine(ZakatKey.INVESTMENTS, i.investments),
                ZakatLine(ZakatKey.RECEIVABLES, i.receivables),
                ZakatLine(ZakatKey.INVENTORY, i.inventory),
                ZakatLine(ZakatKey.GROSS, gross),
                ZakatLine(ZakatKey.DEBTS, -i.debtsDue),
                ZakatLine(ZakatKey.NET, net),
                ZakatLine(ZakatKey.NISAB, nisab),
            ),
            net = net,
            nisab = nisab,
            reachedNisab = reached,
            hawlComplete = i.hawlComplete,
            due = due,
        )
    }
}
