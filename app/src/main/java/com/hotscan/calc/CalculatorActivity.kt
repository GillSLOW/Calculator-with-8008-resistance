package com.hotscan.calc

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.hotscan.calc.databinding.ActivityCalculatorBinding
import java.text.DecimalFormat

/**
 * A fully working basic calculator (same feature set as a stock phone calculator:
 * + - × ÷ %, sign toggle, decimal, clear, backspace).
 *
 * Hidden behaviour: whenever the raw digits currently typed equal the configured
 * unlock code, the Signal Monitor screen opens instead of showing a result.
 */
class CalculatorActivity : AppCompatActivity() {

    private lateinit var b: ActivityCalculatorBinding
    private lateinit var prefs: Prefs

    private var currentInput = "0"
    private var storedValue: Double? = null
    private var pendingOp: Char? = null
    private var justEvaluated = false

    private val fmt = DecimalFormat("#.##########")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityCalculatorBinding.inflate(layoutInflater)
        setContentView(b.root)
        prefs = Prefs(this)

        val digits = mapOf(
            b.btn0 to "0", b.btn1 to "1", b.btn2 to "2", b.btn3 to "3",
            b.btn4 to "4", b.btn5 to "5", b.btn6 to "6", b.btn7 to "7",
            b.btn8 to "8", b.btn9 to "9"
        )
        digits.forEach { (btn, d) -> btn.setOnClickListener { onDigit(d) } }

        b.btnDot.setOnClickListener { onDot() }
        b.btnClear.setOnClickListener { clearAll() }
        b.btnBack.setOnClickListener { onBackspace() }
        b.btnSign.setOnClickListener { onSign() }
        b.btnPercent.setOnClickListener { onPercent() }

        b.btnAdd.setOnClickListener { onOperator('+') }
        b.btnSub.setOnClickListener { onOperator('-') }
        b.btnMul.setOnClickListener { onOperator('*') }
        b.btnDiv.setOnClickListener { onOperator('/') }
        b.btnEquals.setOnClickListener { onEquals() }

        render()
    }

    override fun onResume() {
        super.onResume()
        // Reset to a clean calculator each time we come back from the monitor.
        clearAll()
    }

    private fun onDigit(d: String) {
        if (justEvaluated) {
            currentInput = "0"
            justEvaluated = false
        }
        currentInput = if (currentInput == "0") d else currentInput + d

        // Code checks — compare exactly what has been typed so far.
        when (currentInput) {
            prefs.panicCode.takeIf { it.isNotEmpty() } -> { panicWipe(); return }
            prefs.decoyCode.takeIf { it.isNotEmpty() } -> { launchDecoy(); return }
            prefs.unlockCode -> { launchMonitor(); return }
        }

        // Easter egg: 8008 / 58008 read upside-down. Snark instead of the number.
        if (currentInput == "8008" || currentInput == "58008") { showSnark(); return }

        render()
    }

    private fun onDot() {
        if (justEvaluated) {
            currentInput = "0"
            justEvaluated = false
        }
        if (!currentInput.contains(".")) currentInput += "."
        render()
    }

    private fun onBackspace() {
        if (justEvaluated) return
        currentInput = if (currentInput.length > 1) currentInput.dropLast(1) else "0"
        if (currentInput == "-" || currentInput.isEmpty()) currentInput = "0"
        render()
    }

    private fun onSign() {
        if (currentInput == "0") return
        currentInput = if (currentInput.startsWith("-")) currentInput.drop(1) else "-$currentInput"
        render()
    }

    private fun onPercent() {
        val v = currentInput.toDoubleOrNull() ?: return
        currentInput = fmt.format(v / 100.0)
        justEvaluated = false
        render()
    }

    private fun onOperator(op: Char) {
        val v = currentInput.toDoubleOrNull() ?: return
        if (storedValue != null && pendingOp != null && !justEvaluated) {
            storedValue = apply(storedValue!!, v, pendingOp!!)
        } else {
            storedValue = v
        }
        pendingOp = op
        justEvaluated = true // next digit starts a fresh operand
        currentInput = fmt.format(storedValue)
        render(showStored = true)
    }

    private fun onEquals() {
        val v = currentInput.toDoubleOrNull() ?: return
        val s = storedValue
        val op = pendingOp
        if (s != null && op != null) {
            val result = apply(s, v, op)
            currentInput = fmt.format(result)
            storedValue = null
            pendingOp = null
            justEvaluated = true
        }
        render()
    }

    private fun apply(a: Double, bb: Double, op: Char): Double = when (op) {
        '+' -> a + bb
        '-' -> a - bb
        '*' -> a * bb
        '/' -> if (bb == 0.0) Double.NaN else a / bb
        else -> bb
    }

    private fun clearAll() {
        currentInput = "0"
        storedValue = null
        pendingOp = null
        justEvaluated = false
        render()
    }

    private fun render(showStored: Boolean = false) {
        b.display.text = when {
            currentInput == "NaN" -> "Error"
            else -> currentInput
        }
    }

    private fun launchMonitor() {
        clearAll()
        startActivity(Intent(this, ScannerActivity::class.java))
    }

    private val snarks = listOf(
        "perv", "grow up", "act your age", "take a cold shower", "dumb",
        "middle schooler", "really?", "how original", "we've all seen it", "🙄 classic"
    )

    private fun showSnark() {
        clearAll()
        b.display.text = snarks.random()
    }

    private fun launchDecoy() {
        clearAll()
        startActivity(Intent(this, DecoyActivity::class.java))
    }

    /** Panic code: stop scanning and delete all log/export files, then act like a calculator. */
    private fun panicWipe() {
        clearAll()
        try {
            startService(Intent(this, ScanService::class.java).apply { action = ScanService.ACTION_STOP })
        } catch (_: Exception) {}
        try {
            java.io.File(getExternalFilesDir(null), "logs").deleteRecursively()
            java.io.File(getExternalFilesDir(null), "exports").deleteRecursively()
        } catch (_: Exception) {}
    }
}
