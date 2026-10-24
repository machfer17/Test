package com.deliveryanalyzer.app

/** Pure Kotlin parser: candidates are read from the screen, ordered by their visual position. */
object OfferAddressParser {
    data class ScreenText(val text: String, val top: Int, val left: Int)
    data class Detection(val address: String?, val explanation: String)

    private val accept = Regex("(?i)^\\s*accepter\\s*$")
    private val streetStart = Regex("(?i)\\b\\d{1,4}\\s*(?:bis|ter)?\\s+(?:rue|avenue|av\\.?|boulevard|bd\\.?|allée|allee|impasse|chemin|place|route|passage|square|quai|sentier|cours)\\b")
    private val postal = Regex("\\b\\d{5}\\b")
    private val completePostal = Regex("\\b\\d{5}\\s*,?\\s*\\p{L}")
    private val likelyContinuation = Regex("^[\\p{L}][\\p{L}\\s'’\\-]{1,70}$")

    fun detect(items: List<ScreenText>): Detection {
        val rows = items.flatMap { item ->
            item.text.lines().filter { it.isNotBlank() }.mapIndexed { index, value ->
                ScreenText(value.trim(), item.top + index, item.left)
            }
        }.filter { it.text.isNotBlank() }
            .distinctBy { Triple(it.text, it.top, it.left) }
            .sortedWith(compareBy<ScreenText> { it.top }.thenBy { it.left })

        val acceptIndex = rows.indexOfLast { accept.matches(it.text) }
        if (acceptIndex < 0) return Detection(null, "Bouton Accepter non trouvé : adresse client non identifiable.")
        val before = rows.take(acceptIndex)
        // Closest street address to 'Accepter' is the customer. Do NOT select the first address on screen.
        val start = before.indexOfLast { streetStart.containsMatchIn(it.text) }
        if (start < 0) return Detection(null, "Aucune adresse de rue au-dessus d'Accepter.")
        val candidate = before[start].text.substring(streetStart.find(before[start].text)!!.range.first).trim()
        val parts = mutableListOf(candidate)
        // Second line can contain the postal code/city, as in: '4 Rue du Chêne Germain, 35510, Cesson-' / 'Sévigné'.
        for (index in start + 1 until minOf(before.size, start + 4)) {
            val extra = before[index].text.trim()
            if (accept.matches(extra) || streetStart.containsMatchIn(extra)) break
            if (extra.matches(Regex("(?i).*(?:€|km|min|ajouter une livraison).*"))) break
            if (extra.length > 75 || !extra.first().isLetterOrDigit()) break
            if (!postal.containsMatchIn(parts.joinToString(" ")) || parts.last().endsWith("-") ||
                (postal.containsMatchIn(extra) || likelyContinuation.matches(extra))) {
                parts.add(extra)
            } else break
            if (parts.size >= 3) break
        }
        val address = parts.joinToString(" ").replace(Regex("-\\s+(?=\\p{L})"), "-")
            .replace(Regex("\\s+"), " ").trim().trimEnd(',', '.')
        if (!postal.containsMatchIn(address) || !completePostal.containsMatchIn(address)) {
            return Detection(null, "Adresse partielle détectée : $address. Ville/code postal non confirmés.")
        }
        return Detection(address, "Adresse cliente trouvée au-dessus du bouton Accepter.")
    }
}
