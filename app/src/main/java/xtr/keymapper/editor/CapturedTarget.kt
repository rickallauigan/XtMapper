package xtr.keymapper.editor

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import xtr.keymapper.controller.ControllerBindings

/** Measurement metadata, independent of runtime mappings and profile ZIP backups. */
data class CapturedTarget(val id: String, val name: String, val description: String,
    val x: Float, val y: Float, val width: Int, val height: Int, val capturedAt: Long,
    val profileId: String? = null, val gamePackage: String? = null, val input: String = "DPAD_UP", val sourceWidth: Int = width, val sourceHeight: Int = height) {
    init {
        require(id.isNotBlank() && id.length <= 100)
        require(name.isNotBlank() && name.length <= 80 && name.none { it.isISOControl() }) { "Name: 1–80 printable characters" }
        require(description.length <= 1000 && description.none { it.isISOControl() && it != '\n' }) { "Description: up to 1000 characters" }
        require(width > 0 && height > 0 && x.isFinite() && y.isFinite() && x >= 0 && y >= 0 && x < width && y < height)
        require(listOfNotNull(profileId, gamePackage).all {it.length <= 200 && it.none {c -> c.isISOControl()}})
        require(sourceWidth > 0 && sourceHeight > 0)
        require(capturedAt >= 0 && ControllerBindings.isController(input))
    }
    fun coordinates() = "$x, $y"
    fun snippetJson(): String = JSONObject().put("name", name).put("description", description)
        .put("input", input).put("action", "fixed_touch").put("isEnabled", true)
        .put("position", JSONObject().put("x", x.toDouble()).put("y", y.toDouble())).toString(2)
    // JSON double-quoted scalars are valid YAML scalars, including Unicode/newlines.
    fun snippetYaml() = "name: ${JSONObject.quote(name)}\ndescription: ${JSONObject.quote(description)}\ninput: $input\naction: fixed_touch\nisEnabled: true\nposition:\n  x: $x\n  y: $y\n"
    fun pointFor(profileWidth: Int, profileHeight: Int, application: String): Pair<Float, Float> {
        require(gamePackage.isNullOrEmpty() || gamePackage == application) { "Target belongs to another game" }
        require(profileWidth == width && profileHeight == height) { "Target plane differs; recapture for this profile" }
        return x to y
    }
    internal fun json() = JSONObject(snippetJson()).put("id", id).put("width", width).put("height", height)
        .put("sourceWidth", sourceWidth).put("sourceHeight", sourceHeight).put("orientation", if (sourceWidth > sourceHeight) "landscape" else "portrait").put("capturedAt", capturedAt).put("profileId", profileId ?: JSONObject.NULL).put("gamePackage", gamePackage ?: JSONObject.NULL)
    companion object {
        internal fun parse(j: JSONObject): CapturedTarget {
            val p = j.getJSONObject("position")
            fun number(o: JSONObject, key: String): Double { val n = o.get(key); require(n is Number); return n.toDouble().also { require(it.isFinite()) } }
            fun integer(key: String): Int { val n = number(j, key); require(n == n.toInt().toDouble()); return n.toInt() }
            fun text(key: String): String { val v = j.get(key); require(v is String); return v }
            fun optional(key: String) = if (j.isNull(key)) null else text(key)
            val timestamp = number(j, "capturedAt"); require(timestamp >= 0 && timestamp < Long.MAX_VALUE.toDouble() && timestamp == timestamp.toLong().toDouble())
            require(j.get("isEnabled") == true && text("action") == "fixed_touch")
            return CapturedTarget(text("id"), text("name"), text("description"), number(p,"x").toFloat(), number(p,"y").toFloat(),
                integer("width"), integer("height"), timestamp.toLong(), optional("profileId"), optional("gamePackage"), text("input"),
                if (j.has("sourceWidth")) integer("sourceWidth") else integer("width"), if (j.has("sourceHeight")) integer("sourceHeight") else integer("height"))
        }
    }
}

class CapturedTargetStore(context: Context) {
    private val prefs = context.getSharedPreferences("captured_targets_v1", Context.MODE_PRIVATE)
    fun read(): List<CapturedTarget> = decode(prefs.getString("targets", "[]")!!)
    fun save(expected: List<CapturedTarget>, next: List<CapturedTarget>) {
        require(next.size <= 200 && next.map { it.id }.distinct().size == next.size) { "Too many or duplicate target identities" }
        synchronized(LOCK) {
            require(read() == expected) { "Target library changed elsewhere. Reopen; nothing overwritten." }
            val prior = prefs.getString("targets", null)
            val encoded = JSONArray().also { a -> next.forEach { a.put(it.json()) } }.toString()
            if (!prefs.edit().putString("targets", encoded).commit()) {
                // commit() may already have changed memory even when disk persistence failed.
                val rollback = prefs.edit()
                if (prior == null) rollback.remove("targets") else rollback.putString("targets", prior)
                if (!rollback.commit()) error("Target save failed; rollback persistence also failed. Verify storage before retrying.")
                error("Target save failed; prior state restored.")
            }
        }
    }
    private fun decode(text: String): List<CapturedTarget> {
        require(text.length <= 1_000_000) { "Target library is too large" }
        val a = JSONArray(text); require(a.length() <= 200)
        return (0 until a.length()).map { CapturedTarget.parse(a.getJSONObject(it)) }.also { require(it.map { t -> t.id }.distinct().size == it.size) }
    }
    companion object { private val LOCK = Any() }
}
