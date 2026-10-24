package com.deliveryanalyzer.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import java.util.concurrent.Executors

object DeliveryEngine {
    private val executor = Executors.newSingleThreadExecutor()
    private val street = Regex("(?i)\\b\\d{1,4}\\s*(?:bis|ter)?\\s+(?:rue|avenue|av\\.?|boulevard|bd\\.?|allée|allee|impasse|chemin|place|route|passage|square|quai)\\s+(?:[a-zà-ÿ0-9'’.-]+[\\s-]*){1,7}")
    private val postcode = Regex("\\b(?:75|77|78|91|92|93|94|95)\\d{3}\\b")
    // Intentionally conservative: if the city is missing, ask for it rather than inventing one.
    fun extractAddress(raw: String): String? {
        val lines = raw.lines().map { it.trim() }.filter { it.isNotBlank() }
        for (i in lines.indices) {
            val line = lines[i]
            val match = street.find(line) ?: continue
            val tail = (listOf(line) + lines.drop(i + 1).take(2)).joinToString(", ")
            val cp = postcode.find(tail)
            if (cp != null) return tail.substring(match.range.first).take(155)
            // Cities supplied without zip code sometimes appear after the street on next line.
            val next = lines.getOrNull(i + 1)
            if (next != null && next.matches(Regex("(?i)[A-Za-zÀ-ÿ][A-Za-zÀ-ÿ '-]{2,40}"))) {
                return "${match.value.trim()}, $next"
            }
        }
        return null
    }

    fun analyze(input: String, callback: (String) -> Unit) {
        if (input.isBlank()) { callback("Indique une adresse complète."); return }
        executor.execute {
            try { callback(analyzeBlocking(input)) }
            catch (e: Exception) { callback("Analyse impossible : ${e.message ?: "réseau indisponible"}. Vérifie la connexion et réessaie.") }
        }
    }

    private fun analyzeBlocking(input: String): String {
        val geoUrl = "https://data.geopf.fr/geocodage/search/?q=${URLEncoder.encode(input, "UTF-8")}&limit=1"
        val features = fetch(geoUrl).getJSONArray("features")
        if (features.length() == 0) return "Adresse introuvable : $input. Vérifie la ville et le numéro."
        val feature = features.getJSONObject(0)
        val properties = feature.getJSONObject("properties")
        val label = properties.optString("label", input)
        val score = properties.optDouble("score", 0.0)
        val coords = feature.getJSONObject("geometry").getJSONArray("coordinates")
        val lon = coords.getDouble(0); val lat = coords.getDouble(1)
        if (score < 0.55) return "Adresse peu fiable ($label). Vérifie la saisie avant toute conclusion."

        // OpenStreetMap provides partial observations, not proof of parking legality.
        val overpass = "[out:json][timeout:12];(way(around:65,$lat,$lon)[building];way(around:110,$lat,$lon)[\"parking:lane\"];way(around:110,$lat,$lon)[amenity=parking];node(around:110,$lat,$lon)[amenity=parking]);out tags center;"
        var housing = "Type de bâtiment : indéterminé"
        var parking = "Stationnement public légal : non vérifié"
        var access = "Accès : portail, digicode et entrée non vérifiés"
        var notes = ""
        try {
            val elements = fetch("https://overpass.kumi.systems/api/interpreter?data=${URLEncoder.encode(overpass, "UTF-8")}").getJSONArray("elements")
            val buildings = mutableListOf<String>()
            var hasParking = false
            var parkingLanes = false
            for (i in 0 until elements.length()) {
                val tags = elements.getJSONObject(i).optJSONObject("tags") ?: continue
                val b = tags.optString("building")
                if (b.isNotBlank()) buildings.add(b)
                if (tags.optString("amenity") == "parking") hasParking = true
                if (tags.keys().asSequence().any { it.startsWith("parking:lane") }) parkingLanes = true
            }
            housing = when {
                buildings.isEmpty() -> "Bâtiment : non renseigné dans OSM"
                buildings.all { it in listOf("house", "detached", "semidetached_house", "terrace") } -> "Maisons cartographiées à proximité (adresse non attribuée)"
                buildings.any { it in listOf("apartments", "residential") } -> "Habitat collectif possible à proximité (non confirmé au numéro)"
                else -> "Bâtiments proches : types mixtes ou non précisés"
            }
            parking = when {
                hasParking -> "Parking cartographié à moins de 110 m (accès et légalité à vérifier)"
                parkingLanes -> "Stationnement sur rue renseigné à proximité (règles à vérifier)"
                else -> "Aucune place publique confirmée dans les données interrogées"
            }
        } catch (_: Exception) { notes = "\nCartographie OSM indisponible : analyse partielle." }
        return "📍 $label\n$housing\n🅿 $parking\n🚪 $access\n⚠ Pas de score fiable sans vérification des places, panneaux et accès.\nConfiance géocodage : ${(score * 100).toInt()} %.$notes"
    }

    private fun fetch(url: String): JSONObject {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6000
            readTimeout = 14000
            setRequestProperty("User-Agent", "DeliveryAnalyzerMVP/0.1 (personal use)")
        }
        try {
            if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}")
            return JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally { conn.disconnect() }
    }
}
