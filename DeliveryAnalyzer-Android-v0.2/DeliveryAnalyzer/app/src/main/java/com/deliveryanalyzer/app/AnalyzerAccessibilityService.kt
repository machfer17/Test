package com.deliveryanalyzer.app

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.Rect
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.Button
import android.widget.TextView
import android.view.MotionEvent

class AnalyzerAccessibilityService : AccessibilityService() {
    private lateinit var windowManager: WindowManager
    private var rootWidget: LinearLayout? = null
    private var output: TextView? = null
    private lateinit var params: WindowManager.LayoutParams

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(235, 25, 33, 41))
            setPadding(14, 10, 14, 10)
        }
        val analyze = Button(this).apply { text = "ANALYSER"; setOnClickListener { inspectScreen() } }
        val dismiss = Button(this).apply { text = "×"; setOnClickListener { output?.visibility = android.view.View.GONE } }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; addView(analyze); addView(dismiss) }
        output = TextView(this).apply { setTextColor(Color.WHITE); textSize = 14f; visibility = android.view.View.GONE; setPadding(8, 8, 8, 8) }
        wrapper.addView(header)
        wrapper.addView(output)
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 8; y = 200 }
        // Drag by touching the non-button part of the panel.
        wrapper.setOnTouchListener(object : android.view.View.OnTouchListener {
            private var downX = 0f; private var downY = 0f; private var startX = 0; private var startY = 0
            override fun onTouch(v: android.view.View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> { downX = event.rawX; downY = event.rawY; startX = params.x; startY = params.y; return true }
                    MotionEvent.ACTION_MOVE -> { params.x = startX + (event.rawX - downX).toInt(); params.y = startY + (event.rawY - downY).toInt(); windowManager.updateViewLayout(wrapper, params); return true }
                }
                return false
            }
        })
        rootWidget = wrapper
        windowManager.addView(wrapper, params)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* No background monitoring. */ }
    override fun onInterrupt() { }

    private fun inspectScreen() {
        val visible = mutableListOf<OfferAddressParser.ScreenText>()
        // Read bounding boxes rather than relying on arbitrary accessibility tree order.
        readText(rootInActiveWindow, visible, 0)
        val result = OfferAddressParser.detect(visible)
        val address = result.address
        output?.visibility = android.view.View.VISIBLE
        if (address == null) {
            output?.text = "Adresse client non détectée.\n${result.explanation}\nSi Uber masque les textes à Android, un OCR sera nécessaire."
            return
        }
        output?.text = "Adresse : $address\nAnalyse en cours..."
        DeliveryEngine.analyze(address) { result ->
            android.os.Handler(mainLooper).post { if (rootWidget != null) output?.text = result }
        }
    }

    private fun readText(node: AccessibilityNodeInfo?, dest: MutableList<OfferAddressParser.ScreenText>, depth: Int) {
        if (node == null || depth > 30 || dest.size > 500) return
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (!bounds.isEmpty && node.isVisibleToUser) {
            node.text?.toString()?.takeIf { it.isNotBlank() }?.let {
                dest.add(OfferAddressParser.ScreenText(it, bounds.top, bounds.left))
            }
            // Avoid adding duplicative contentDescription when text is the same.
            node.contentDescription?.toString()?.takeIf { it.isNotBlank() && it != node.text?.toString() }?.let {
                dest.add(OfferAddressParser.ScreenText(it, bounds.top, bounds.left))
            }
        }
        for (i in 0 until node.childCount) readText(node.getChild(i), dest, depth + 1)
    }

    override fun onDestroy() {
        rootWidget?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        rootWidget = null
        super.onDestroy()
    }
}
