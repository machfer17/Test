package com.deliveryanalyzer.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.graphics.Color
import android.view.ViewGroup
import android.widget.*

class MainActivity : Activity() {
    private lateinit var status: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 48, 32, 24) }
        val title = TextView(this).apply { text = "Delivery Analyzer · MVP"; textSize = 24f; setTextColor(Color.BLACK) }
        val info = TextView(this).apply {
            text = "1. Active Delivery Analyzer dans Paramètres > Accessibilité > Applications installées.\n2. Ouvre Uber Driver.\n3. Appuie sur le bouton flottant ANALYSER.\n\nL’outil ne lit que le texte exposé par Android. Il n’accepte jamais de commande."
            textSize = 16f
        }
        status = TextView(this).apply { text = "Autorisation à activer"; textSize = 15f }
        val settings = Button(this).apply { text = "Ouvrir les paramètres d’accessibilité"; setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } }
        val field = EditText(this).apply { hint = "Ex. 1 rue de la Clairière, 95280 Jouy-le-Moutier"; setSingleLine(false); minLines = 2 }
        val button = Button(this).apply { text = "Tester cette adresse"; setOnClickListener {
            status.text = "Analyse en cours..."
            DeliveryEngine.analyze(field.text.toString()) { result -> runOnUiThread { status.text = result } }
        } }
        listOf(title, info, settings, field, button, status).forEach { v -> layout.addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 22 }) }
        val scroll = ScrollView(this).apply { addView(layout) }
        setContentView(scroll)
    }
}
