package xtr.keymapper

import android.content.Context
import xtr.keymapper.controller.ControllerBindings
import xtr.keymapper.devices.*
import xtr.keymapper.keymap.KeymapProfile
import xtr.keymapper.keymap.KeymapProfiles
import xtr.keymapper.keymap.ProfileConfiguration

/** A binding is one persisted action, including compound actions (D-pad, swipe, chord). */
data class MappingRow(val id: String, val input: String, val action: String, val group: String, val parameters: String)
data class ProfileMappingSummary(val rows: List<MappingRow>) {
    val count get() = rows.size
    companion object {
        fun from(profile: KeymapProfile, targets: List<xtr.keymapper.editor.CapturedTarget> = emptyList()): ProfileMappingSummary {
            val rows = mutableListOf<MappingRow>()
            fun label(code: String?) = when (code) {
                null -> "Unknown input"
                "BTN_MOUSE", "MOUSE_LEFT" -> "Mouse Left"
                "BTN_RIGHT", "MOUSE_RIGHT" -> "Mouse Right"
                else -> ControllerBindings.label(code)
            }
            fun group(code: String) = if (ControllerBindings.isController(code)) "Controller" else "Keyboard / mouse"
            fun at(x: Float, y: Float) = "Target ($x, $y)"
            fun add(id: String, input: String, action: String, category: String, params: String) {
                rows += MappingRow(id, input, action, category, params)
            }
            fun named(code: String, x: Float, y: Float, fallback: String): String {
                val matches = targets.filter { it.input == code && (it.gamePackage.isNullOrEmpty() || it.gamePackage == profile.packageName) && it.width == profile.xRes && it.height == profile.yRes && it.x == x && it.y == y }
                return matches.singleOrNull()?.let { "${it.name} • $fallback" } ?: fallback
            }
            profile.keys.forEach { k -> add("fixed:${k.code}", label(k.code), named(k.code, k.x+k.offset, k.y+k.offset, if (k.repeatIntervalMs > 0) "Hold / repeat" else "Touch"), group(k.code),
                "${at(k.x, k.y)} • offset ${k.offset}" + if (k.repeatIntervalMs > 0) " • ${k.repeatIntervalMs} ms" else "") }
            profile.aimKeys.forEach { k -> add("aim:${k.code}", label(k.code), "Hold & aim", group(k.code),
                "${at(k.x, k.y)} • radius ${k.radius} • sensitivity ${k.xSensitivity}/${k.ySensitivity}" +
                    if (k.stick) " • right stick • dead zone ${k.deadZone} • invert Y ${k.invertY}" else " • mouse") }
            profile.chords.forEach { k -> add("chord:${k.modifier}:${k.trigger}", "${label(k.modifier)} + ${label(k.trigger)}", "Chord touch", "Controller", "${at(k.x, k.y)} • offset ${k.offset}") }
            profile.dpadArray.forEachIndexed { i, d -> if (d != null) {
                val codes = listOf(d.keycodes.Up, d.keycodes.Down, d.keycodes.Left, d.keycodes.Right)
                add("dpad:$i", codes.joinToString(" / ") { label(it) }, "Movement pad (4 directions)",
                    if (codes.any { ControllerBindings.isController(it) }) "Controller" else "Keyboard / mouse", "${at(d.xOfCenter, d.yOfCenter)} • radius ${d.radius}")
            } }
            profile.camera?.let { c -> add("camera", if (c.stick) "Right stick" else label(c.triggerKeyCode), "Camera pan", if (c.stick) "Controller" else group(c.triggerKeyCode),
                "${at(c.x, c.y)} • sensitivity ${c.xSensitivity}/${c.ySensitivity}" + if (c.stick) " • dead zone ${c.deadZone}" else " • toggle ${c.toggle} • auto ${c.autoActive}") }
            profile.leftClick?.let { add("left-click", "Mouse Left", "Touch", "Keyboard / mouse", at(it.x, it.y)) }
            profile.rightClick?.let { add("right-click", "Mouse Right", "Touch", "Keyboard / mouse", at(it.x, it.y)) }
            profile.mouseWalk?.let { add("mouse-walk", "Mouse", "Movement", "Keyboard / mouse", "${at(it.x, it.y)} • radius ${it.radius}") }
            profile.mouseAimConfig?.let { add("mouse-aim", "Mouse", "Aim", "Keyboard / mouse", "${at(it.xCenter, it.yCenter)} • bounds ${it.width} × ${it.height} • click (${it.xleftClick}, ${it.yleftClick}) • sensitivity ${it.xSensitivity}/${it.ySensitivity} • limited ${it.limitedBounds} • nonlinear ${it.applyNonLinearScaling}") }
            profile.swipeKeys.forEachIndexed { i, k -> add("swipe:$i", "${label(k.key1.code)} / ${label(k.key2.code)}", "Swipe", "Advanced", "${at(k.key1.x, k.key1.y)} → (${k.key2.x}, ${k.key2.y})") }
            profile.macroIdMap.toSortedMap().forEach { (id, m) -> add("macro:$id", label(m.triggerKey), "Macro", "Advanced", "Saved macro $id • contents not executed or loaded") }
            return ProfileMappingSummary(rows.sortedWith(compareBy<MappingRow> { it.group }.thenBy { it.input }.thenBy { it.id }).mapIndexed { index, row -> row.copy(id = "${row.id}:$index") })
        }
    }
}

data class SavedProfileDetails(val id: String, val packageName: String, val enabled: Boolean,
    val summary: ProfileMappingSummary, val dimensions: String, val error: String? = null,
    val devices: List<String> = emptyList())

/** Reads a fresh snapshot of the requested profile; inspection never writes preferences. */
object ProfileDetailsReader {
    fun read(context: Context, id: String): SavedProfileDetails? {
        val stored = KeymapProfiles(context).sharedPref.all[id] ?: return null
        val lines = (stored as? Set<*>)?.filterIsInstance<String>()?.toSet()
            ?: return SavedProfileDetails(id, "Unknown application", false, ProfileMappingSummary(emptyList()), "Unknown", "Invalid saved profile")
        val packageName = lines.firstOrNull { it.startsWith("APPLICATION ") }?.substringAfter(" ") ?: "Unknown application"
        return try {
            val profile = ProfileConfiguration.parse(lines.sorted().joinToString("\n"))
            val store = DeviceMappingStore(context)
            val connected = InputDeviceDiscovery.connected().map { it.persistentIdentity }.toSet()
            val groups = store.listGroups()
            val associations = store.listBindings().filter { it.profileName == id && it.packageName == packageName }.mapNotNull { b ->
                groups.find { it.id == b.groupId }?.let { g ->
                    val online = g.members.isNotEmpty() && g.members.all { it.identity in connected }
                    "${g.name} • ${if (online) "connected" else "disconnected"} • preferred for this app"
                }
            }
            SavedProfileDetails(id, profile.packageName, !profile.disabled, ProfileMappingSummary.from(profile, try { xtr.keymapper.editor.CapturedTargetStore(context).read() } catch (_: Exception) { emptyList() }), "${profile.xRes} × ${profile.yRes}", devices = associations)
        } catch (e: IllegalArgumentException) {
            SavedProfileDetails(id, packageName, "ENABLED" in lines, ProfileMappingSummary(emptyList()), "Unknown", e.message ?: "Invalid saved profile")
        }
    }
}
