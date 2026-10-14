package xtr.keymapper.editor

import android.content.Context
import xtr.keymapper.controller.ControllerBindings
import xtr.keymapper.keymap.KeymapProfiles
import xtr.keymapper.keymap.ProfileConfiguration

/** UI draft only. Original text is preserved until an explicit, conflict-checked Save. */
class VisualMappingSession(val profileId: String, saved: Set<String>) {
    val original = saved.toSet()
    var lines: Set<String> = original
        private set
    private val history = ArrayDeque<Set<String>>()
    val profile get() = ProfileConfiguration.parse(lines.sorted().joinToString("\n"))
    val dirty get() = lines != original
    init { require(profile.xRes > 0 && profile.yRes > 0) { "Measure/save a profile screen size in Advanced Editor first." } }

    fun bindings(code: String): List<String> = lines.filter { line ->
        val d = line.trim().split(Regex("\\s+"))
        when (d[0]) {
            "STICK_AIM", "AIM_KEY" -> ControllerBindings.canonical(d[1]) == code
            "CHORD" -> ControllerBindings.canonical(d[1]) == code || ControllerBindings.canonical(d[2]) == code
            "SWIPE_KEY" -> ControllerBindings.editorCode(d[1]) == code || ControllerBindings.editorCode(d[4]) == code
            "DPAD", "DPAD_UDLR" -> d.drop(8).any { ControllerBindings.canonical(it) == code }
            "MACRO" -> ControllerBindings.editorCode(d[2]) == code
            "CAMERA" -> d[6] == code
            "STICK_CAMERA" -> code == RIGHT_STICK
            else -> ControllerBindings.canonical(d[0]) == code
        }
    }.sorted()

    fun replace(code: String, replacement: String?, confirmTypeChange: Boolean = false) {
        require(code != "NATIVE_LEFT" && code != "BTN_MODE" && code != "BTN_START") { "Native/reserved control is not editable here." }
        val old = bindings(code)
        require(old.size <= 1) { "Conflicting bindings: resolve in Advanced Editor first." }
        val oldLine = old.singleOrNull()
        val oldTag = ControllerBindings.canonical(oldLine?.substringBefore(' '))
        require(oldTag == null || oldTag == code || oldTag in setOf("STICK_AIM", "STICK_CAMERA")) { "Compound/advanced binding: use Advanced Editor." }
        if (replacement != null) {
            val d = replacement.split(Regex("\\s+"))
            val owns = if (d[0] == "STICK_AIM") d.getOrNull(1) == code else
                if (d[0] == "STICK_CAMERA") code == RIGHT_STICK else d[0] == code
            require(owns) { "Replacement belongs to another input." }
            val oldMode = if (oldTag == code && oldLine?.split(Regex("\\s+"))?.size == 5) "REPEAT" else oldTag
            val newMode = if (d[0] == code && d.size == 5) "REPEAT" else d[0]
            require(oldMode == null || oldMode == newMode || confirmTypeChange) { "Confirm replacing the existing mapping type." }
            require(code != RIGHT_STICK || d[0] == "STICK_CAMERA") { "Analog stick is not a digital button." }
        }
        val next = xtr.keymapper.structured.StructuredProfiles.reconcile(lines, lines - old.toSet() + listOfNotNull(replacement))
        ProfileConfiguration.validate(next.sorted().joinToString("\n"))
        if (next != lines) { history.addLast(lines); lines = next }
    }
    fun applyTarget(code: String, target: CapturedTarget, confirm: Boolean = false) {
        require(confirm) { "Confirm the old/new binding before applying a saved target" }
        replace(code, targetReplacement(code, target), true)
    }
    fun targetReplacement(code: String, target: CapturedTarget): String {
        val (x, y) = target.pointFor(profile.xRes, profile.yRes, profile.packageName)
        require(code != RIGHT_STICK && ControllerBindings.isController(code))
        val old = bindings(code).singleOrNull()?.split(Regex("\\s+"))
        // A skill target changes its center, retaining the accepted analog tuning.
        if (old?.firstOrNull() == "STICK_AIM") return "STICK_AIM $code $x $y " + old.drop(4).joinToString(" ")
        // Fixed-touch target selection is explicitly nonrepeating, with no legacy marker offset.
        return "$code $x $y 0"
    }
    fun chord(modifier: String, trigger: String, x: Float?, y: Float?) {
        val m = ControllerBindings.canonical(modifier); val t = ControllerBindings.canonical(trigger)
        val old = lines.filter { val d=it.split(Regex("\\s+")); d.size==6 && d[0]=="CHORD" && ControllerBindings.canonical(d[1])==m && ControllerBindings.canonical(d[2])==t }
        val replacement = if (x == null && y == null) emptyList() else {
            require(x != null && y != null); listOf("CHORD $m $t $x $y 0")
        }
        val next=xtr.keymapper.structured.StructuredProfiles.reconcile(lines,lines-old.toSet()+replacement)
        ProfileConfiguration.validate(next.sorted().joinToString("\n"))
        if(next!=lines) {history.addLast(lines); lines=next}
    }
    fun undo() { if (history.isNotEmpty()) lines = history.removeLast() }
    fun reset() { if (dirty) { history.addLast(lines); lines = original } }
    fun resetBinding(code: String) {
        val restored = VisualMappingSession(profileId, original).bindings(code)
        val next = xtr.keymapper.structured.StructuredProfiles.reconcile(lines, lines - bindings(code).toSet() + restored)
        ProfileConfiguration.validate(next.sorted().joinToString("\n"))
        if (next != lines) { history.addLast(lines); lines = next }
    }
    fun save(context: Context) {
        val prefs = KeymapProfiles(context).sharedPref
        synchronized(SAVE_LOCK) {
            val current = prefs.getStringSet(profileId, null)?.toSet()
            require(current != null) { "Profile was deleted or renamed. Nothing saved." }
            require(current == original) { "Profile changed elsewhere. Discard and reopen; nothing overwritten." }
            ProfileConfiguration.validate(lines.sorted().joinToString("\n"))
            xtr.keymapper.structured.StructuredProfiles.writeSet(prefs,profileId,lines)
        }
    }
    companion object { const val RIGHT_STICK = "RIGHT_STICK"; private val SAVE_LOCK = xtr.keymapper.structured.StructuredProfiles.SAVE_LOCK }
}

/** Full profile plane, with only a measured viewport origin (never a guessed cutout offset). */
data class TargetPlane(val width: Float, val height: Float, val left: Float = 0f, val top: Float = 0f,
                       val profileWidth: Float, val profileHeight: Float) {
    init { require(listOf(width, height, profileWidth, profileHeight).all { it.isFinite() && it > 0 }); require(left.isFinite() && top.isFinite()) }
    fun toProfile(x: Float, y: Float): Pair<Float, Float> {
        require(x.isFinite() && y.isFinite())
        return (((x - left) / width * profileWidth).coerceIn(0f, profileWidth - 1f)) to
            (((y - top) / height * profileHeight).coerceIn(0f, profileHeight - 1f))
    }
    fun toViewport(x: Float, y: Float) = (left + x / profileWidth * width) to (top + y / profileHeight * height)
}
