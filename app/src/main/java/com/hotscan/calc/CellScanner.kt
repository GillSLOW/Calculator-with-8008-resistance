package com.hotscan.calc

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.CellInfo
import android.telephony.CellInfoCdma
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.CellIdentityNr
import android.telephony.CellSignalStrengthNr
import android.telephony.TelephonyManager

/**
 * Logs the cell towers the phone's radio can see via TelephonyManager, and applies
 * simple heuristics to flag unusual ("rogue") cells — the kind of indicators an
 * IMSI-catcher / fake base station can produce. These are HEURISTICS, not proof:
 * a flag means "worth a second look," not "confirmed rogue."
 */
@SuppressLint("MissingPermission") // ACCESS_FINE_LOCATION requested at runtime by ScannerActivity
class CellScanner(
    private val context: Context,
    private val cb: SignalCallback,
    intervalMs: Long = 12_000,
    private val rogueDetect: Boolean = true
) : Scanner {

    private val period = intervalMs.coerceAtLeast(8_000)

    private val tm = context.applicationContext
        .getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    // Cells (by key) already seen this session, so a brand-new strong cell stands out.
    private val seen = HashSet<String>()

    private data class Rec(
        val type: String,
        val mcc: String?,
        val mnc: String?,
        val id: String,
        val area: String,
        val dbm: Int,
        val registered: Boolean
    )

    private val poll = object : Runnable {
        override fun run() {
            scanOnce()
            if (running) handler.postDelayed(this, period)
        }
    }

    override fun start() {
        if (running) return
        running = true
        handler.post(poll)
    }

    override fun stop() {
        running = false
        handler.removeCallbacks(poll)
    }

    private fun scanOnce() {
        val infos: List<CellInfo> = try {
            tm.allCellInfo ?: emptyList()
        } catch (_: SecurityException) {
            cb("CELL", "Missing permission for cell info.", false); emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        if (infos.isEmpty()) return

        val simMcc: String?
        val simMnc: String?
        val simOp = try { tm.simOperator } catch (_: Exception) { null } // MCC+MNC
        if (!simOp.isNullOrBlank() && simOp.length >= 5) {
            simMcc = simOp.substring(0, 3)
            simMnc = simOp.substring(3)
        } else { simMcc = null; simMnc = null }

        for (info in infos) {
            val rec = toRec(info) ?: continue
            val tag = if (rec.registered) "REG" else "nbr"
            cb(
                "CELL",
                "[$tag] ${rec.type}  MCC=${rec.mcc ?: "?"} MNC=${rec.mnc ?: "?"}  " +
                    "id=${rec.id} area=${rec.area}  ${rec.dbm}dBm",
                false
            )
            if (rogueDetect) evaluateRogue(rec, simMcc, simMnc)
        }
    }

    private fun evaluateRogue(rec: Rec, simMcc: String?, simMnc: String?) {
        // Only scrutinise the serving/registered cell for most heuristics.
        if (rec.registered) {
            // 1) Operator mismatch vs the installed SIM.
            if (simMcc != null && simMnc != null && rec.mcc != null && rec.mnc != null) {
                if (rec.mcc != simMcc || rec.mnc != simMnc) {
                    alert("Serving cell operator ${rec.mcc}/${rec.mnc} != SIM $simMcc/$simMnc")
                }
            }
            // 2) 2G/GSM serving cell — classic downgrade used to force weak/no encryption.
            if (rec.type == "GSM") {
                alert("Serving cell is 2G/GSM (possible downgrade) id=${rec.id}")
            }
            // 3) Incomplete identity on a serving cell.
            if (rec.mcc == null || rec.mnc == null || rec.id == "?" || rec.area == "?") {
                alert("Serving cell has incomplete identity (${rec.type})")
            }
            // 4) Never-seen cell with abnormally strong signal.
            val key = "${rec.type}:${rec.mcc}/${rec.mnc}:${rec.id}:${rec.area}"
            if (rec.dbm > -60 && rec.dbm != 0 && !seen.contains(key)) {
                alert("New strong serving cell ${rec.dbm}dBm id=${rec.id}")
            }
        }
        seen.add("${rec.type}:${rec.mcc}/${rec.mnc}:${rec.id}:${rec.area}")
    }

    private fun alert(msg: String) = cb("ALERT", "⚠ $msg", true)

    private fun toRec(info: CellInfo): Rec? {
        val reg = info.isRegistered
        return when (info) {
            is CellInfoLte -> {
                val id = info.cellIdentity
                Rec("LTE", mccOf(id.mccString, id.mcc), mncOf(id.mncString, id.mnc),
                    norm(id.ci), norm(id.tac), info.cellSignalStrength.dbm, reg)
            }
            is CellInfoGsm -> {
                val id = info.cellIdentity
                Rec("GSM", mccOf(id.mccString, id.mcc), mncOf(id.mncString, id.mnc),
                    norm(id.cid), norm(id.lac), info.cellSignalStrength.dbm, reg)
            }
            is CellInfoWcdma -> {
                val id = info.cellIdentity
                Rec("WCDMA", mccOf(id.mccString, id.mcc), mncOf(id.mncString, id.mnc),
                    norm(id.cid), norm(id.lac), info.cellSignalStrength.dbm, reg)
            }
            is CellInfoCdma -> {
                val id = info.cellIdentity
                Rec("CDMA", null, null, norm(id.basestationId), norm(id.networkId),
                    info.cellSignalStrength.dbm, reg)
            }
            else -> nrRec(info, reg)
        }
    }

    private fun nrRec(info: CellInfo, reg: Boolean): Rec? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && info is CellInfoNr) {
            val id = info.cellIdentity
            val ss = info.cellSignalStrength
            val mcc: String?; val mnc: String?; val nci: String; val tac: String
            if (id is CellIdentityNr) {
                mcc = id.mccString; mnc = id.mncString
                nci = if (id.nci == Long.MAX_VALUE) "?" else id.nci.toString()
                tac = norm(id.tac)
            } else { mcc = null; mnc = null; nci = "?"; tac = "?" }
            val dbm = if (ss is CellSignalStrengthNr) ss.dbm else ss.dbm
            return Rec("NR(5G)", mcc, mnc, nci, tac, dbm, reg)
        }
        return null
    }

    private val UNAVAILABLE = Int.MAX_VALUE

    private fun norm(v: Int): String = if (v == UNAVAILABLE || v == -1) "?" else v.toString()

    private fun mccOf(str: String?, intVal: Int): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            str?.takeIf { it.isNotBlank() }
        else if (intVal == UNAVAILABLE) null else intVal.toString()

    private fun mncOf(str: String?, intVal: Int): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            str?.takeIf { it.isNotBlank() }
        else if (intVal == UNAVAILABLE) null else intVal.toString()
}
