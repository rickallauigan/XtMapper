package xtr.keymapper.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import xtr.keymapper.ProfileMappingSummary
import xtr.keymapper.controller.ControllerBindings

/** Controller selection and measured placement reuse EditorUI's fullscreen/overlay lifecycle. */
@Composable
fun VisualControllerEditor(session: VisualMappingSession, overlay: Boolean, ready: Boolean,
    closeRequested: Boolean, onKeepEditing: () -> Unit, onSave: () -> String?, onClose: () -> Unit,
    onPlaceTarget: () -> Unit, deviceDescription: String, applicationName: String = session.profile.packageName) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var revision by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<String?>(null) }
    var placing by remember { mutableStateOf(false) }
    var point by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var mode by remember { mutableStateOf("Touch") }
    var radius by remember { mutableStateOf("180") }
    var repeat by remember { mutableStateOf("150") }
    var xSensitivity by remember { mutableStateOf("1") }
    var ySensitivity by remember { mutableStateOf("1") }
    var deadZone by remember { mutableStateOf("0.15") }
    var invert by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmation by remember { mutableStateOf<(() -> Unit)?>(null) }
    var advanced by remember { mutableStateOf(false) }
    var coordinatesDialog by remember { mutableStateOf(false) }
    var coordinateX by remember { mutableStateOf("") }
    var coordinateY by remember { mutableStateOf("") }
    var targetPicker by remember { mutableStateOf(false) }
    var targetProposal by remember { mutableStateOf<CapturedTarget?>(null) }
    var chordEditor by remember { mutableStateOf(false) }
    var rearVerified by remember { mutableStateOf(false) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val profile = remember(revision) { session.profile }
    val context = androidx.compose.ui.platform.LocalContext.current
    val rows = remember(revision) { ProfileMappingSummary.from(profile, try { CapturedTargetStore(context).read() } catch (_: Exception) { emptyList() }).rows }
    fun load(code: String) {
        selected = code; error = null; placing = false; advanced = false
        val old = session.bindings(code).singleOrNull()?.split(Regex("\\s+"))
        point = null; mode = "Touch"; radius = "180"; repeat = "150"
        xSensitivity = "1"; ySensitivity = "1"; deadZone = "0.15"; invert = false
        if (old != null) when (ControllerBindings.canonical(old[0])) {
            "STICK_AIM" -> {
                mode = "Hold & aim"; point = old[2].toFloat() to old[3].toFloat(); radius = old[4]
                xSensitivity = old[5]; ySensitivity = old[6]; deadZone = old[7]; invert = old[8] == "1"
            }
            "STICK_CAMERA" -> {
                mode = "Camera"; point = old[1].toFloat() to old[2].toFloat()
                xSensitivity = old[3]; ySensitivity = old[4]; deadZone = old[5]
            }
            code -> {
                point = (old[1].toFloat() + old[3].toFloat()) to (old[2].toFloat() + old[3].toFloat())
                if (old.size == 5) { mode = "Repeat"; repeat = old[4] }
            }
        }
        if (code == VisualMappingSession.RIGHT_STICK) mode = "Camera"
    }
    fun apply() {
        val code = selected ?: return
        val p = point ?: run { error = "Place a target on the actual screen first."; return }
        val previous = session.bindings(code).singleOrNull()?.split(Regex("\\s+"))
        val offset = "0" // Point is the effective center, including any legacy offset on load.
        val line = when (mode) {
            "Hold & aim" -> "STICK_AIM $code ${p.first} ${p.second} $radius $xSensitivity $ySensitivity $deadZone ${if (invert) 1 else 0}"
            "Camera" -> "STICK_CAMERA ${p.first} ${p.second} $xSensitivity $ySensitivity $deadZone"
            "Repeat" -> "$code ${p.first} ${p.second} $offset $repeat"
            else -> "$code ${p.first} ${p.second} $offset"
        }
        val write = {
            try { session.replace(code, line, true); revision++; placing = false; error = null }
            catch (e: IllegalArgumentException) { error = e.message }
        }
        val oldMode = when (ControllerBindings.canonical(previous?.firstOrNull())) { "STICK_AIM" -> "Hold & aim"; "STICK_CAMERA" -> "Camera"; code -> if (previous?.size == 5) "Repeat" else "Touch"; else -> null }
        if (oldMode != null && oldMode != mode) confirmation = write else write()
    }
    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFFF5477), secondary = Color(0xFFB9A1FF))) {
        Box(Modifier.fillMaxSize().onSizeChanged { size = it }) {
            val plane = if (size.width > 0 && size.height > 0) TargetPlane(size.width.toFloat(), size.height.toFloat(),
                profileWidth = profile.xRes.toFloat(), profileHeight = profile.yRes.toFloat()) else null
            if (placing && plane != null) {
                Canvas(Modifier.fillMaxSize().testTag("target-plane")
                    .pointerInput(plane, selected) { detectTapGestures { point = plane.toProfile(it.x, it.y) } }
                    .pointerInput(plane, selected) {
                        detectDragGestures { change, _ -> change.consume(); point = plane.toProfile(change.position.x, change.position.y) }
                    }) {
                    val saved = profile.keys.map { it.x to it.y } + profile.aimKeys.map { it.x to it.y }
                    saved.forEach { p ->
                        val v = plane.toViewport(p.first, p.second)
                        drawCircle(Color.White.copy(alpha = .5f), 7.dp.toPx(), Offset(v.first, v.second))
                    }
                    point?.let { p ->
                        val v = plane.toViewport(p.first, p.second); val center = Offset(v.first, v.second)
                        drawCircle(Color(0xFFFF5477), 12.dp.toPx(), center)
                        drawLine(Color.White, center - Offset(22.dp.toPx(), 0f), center + Offset(22.dp.toPx(), 0f), 2.dp.toPx())
                        drawLine(Color.White, center - Offset(0f, 22.dp.toPx()), center + Offset(0f, 22.dp.toPx()), 2.dp.toPx())
                        if (mode == "Hold & aim") radius.toFloatOrNull()?.takeIf { it.isFinite() && it > 0 }?.let {
                            drawCircle(Color(0xFFB9A1FF), it * minOf(plane.width / plane.profileWidth, plane.height / plane.profileHeight), center,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                        }
                    }
                }
                Surface(Modifier.align(Alignment.BottomEnd).widthIn(max = 390.dp).padding(12.dp).navigationBarsPadding(),
                    shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
                    Column(Modifier.padding(12.dp)) {
                        Text("$selected • Tap or drag the real target", style = MaterialTheme.typography.titleSmall)
                        Text(point?.let { "(${it.first}, ${it.second})" } ?: "No target measured yet")
                        Row {
                            TextButton(onClick = { placing = false; selected?.let(::load) }) { Text("Cancel target") }
                            Button(onClick = { placing = false }) { Text("Review target") }
                        }
                    }
                }
            } else {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.safeDrawingPadding().padding(12.dp).verticalScroll(rememberScrollState())) {
                        Text("Controller • ${session.profileId}${if (session.dirty) " • Unsaved" else ""}", style = MaterialTheme.typography.titleLarge)
                        Text(applicationName, style = MaterialTheme.typography.bodyMedium)
                        Text(deviceDescription, style = MaterialTheme.typography.bodySmall)
                        if (!overlay) Text("Fullscreen fallback: open the game behind the editor using overlay permission for live HUD measurement. Do not place unseen game targets.", color = MaterialTheme.colorScheme.secondary)
                        Text("Profile plane ${profile.xRes} × ${profile.yRes}. Recalibrate if the HUD or orientation changes.", style = MaterialTheme.typography.bodySmall)
                        xtr.keymapper.structured.StructuredProfiles.document(session.lines)?.bindings?.filterNot {it.enabled}?.takeIf {it.isNotEmpty()}?.let { disabled ->
                            Text("Disabled mappings retained: " + disabled.joinToString {"${it.name} (${it.input})"} + ". Enable/edit metadata in JSON/YAML profile files.", style=MaterialTheme.typography.bodySmall)
                        }
                        ControllerDiagram(session, selected, ::load)
                        TextButton(onClick = { chordEditor = true }) { Text("Edit controller chords") }
                        Text(ControllerPresentation.rearNotice, style = MaterialTheme.typography.bodySmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(rearVerified, { rearVerified = it })
                            Text("I verified the S10e R4→R3 / L4→L3 copies", style = MaterialTheme.typography.bodySmall)
                        }
                        if (rearVerified) Row {
                            OutlinedButton(onClick = { load("BTN_THUMBR") }) { Text("R4 / R3 copy") }
                            OutlinedButton(onClick = { load("BTN_THUMBL") }) { Text("L4 / L3 copy") }
                        }
                        Text("All controls • accessible list", style = MaterialTheme.typography.titleMedium)
                        ControllerPresentation.controls.chunked(3).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                row.forEach { control ->
                                    OutlinedButton(onClick = { load(control.code) }, modifier = Modifier.weight(1f), enabled = !control.reserved) {
                                        Text("${control.label}\n${if (control.reserved) "Native / reserved" else if (session.bindings(control.code).isEmpty()) "Unmapped" else "Mapped"}")
                                    }
                                }
                            }
                        }
                        selected?.let { code ->
                            HorizontalDivider(Modifier.padding(vertical = 12.dp))
                            Text(ControllerPresentation.controls.firstOrNull { it.code == code }?.label ?: ControllerBindings.label(code), style = MaterialTheme.typography.titleMedium)
                            val existing = session.bindings(code)
                            val currentRow = rows.firstOrNull { it.id.startsWith("fixed:$code:") || it.id.startsWith("aim:$code:") || code == VisualMappingSession.RIGHT_STICK && it.id.startsWith("camera:") }
                            Text(if (existing.isEmpty()) "Unmapped — no target assigned" else if (existing.size > 1) "Conflicting bindings — use Advanced Editor" else currentRow?.action ?: "Compound / advanced mapping")
                            currentRow?.let { Text(it.parameters, style = MaterialTheme.typography.bodySmall) }
                            TextButton(onClick = { advanced = !advanced }) { Text("Advanced input identity") }
                            if (advanced) Text("$code\n${existing.joinToString("\n")}", style = MaterialTheme.typography.bodySmall)
                            val editable = existing.isEmpty() || existing.size == 1 &&
                                ControllerBindings.canonical(existing.single().substringBefore(' ')) in setOf(code, "STICK_AIM", "STICK_CAMERA")
                            if (!editable) Text("Compound/chord/swipe/macro inputs are preserved. Use Advanced Editor for this action.")
                            else {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    val modes = if (code == VisualMappingSession.RIGHT_STICK) listOf("Camera") else listOf("Touch", "Repeat", "Hold & aim")
                                    modes.forEach { choice -> FilterChip(selected = mode == choice, onClick = { mode = choice }, label = { Text(choice) }) }
                                }
                                Text("Touch holds DOWN until release. Repeat emits taps while held. Aim uses the right stick and releases to cast.", style = MaterialTheme.typography.bodySmall)
                                if (mode == "Repeat") OutlinedTextField(repeat, { repeat = it }, label = { Text("Repeat interval (60–60000 ms)") })
                                if (mode == "Hold & aim") {
                                    OutlinedTextField(radius, { radius = it }, label = { Text("Aim radius") })
                                    val r = radius.toFloatOrNull()?.takeIf { it.isFinite() }?.coerceIn(1f, minOf(profile.xRes, profile.yRes).toFloat()) ?: 180f
                                    Slider(r, { radius = it.toInt().toString() }, valueRange = 1f..minOf(profile.xRes, profile.yRes).toFloat())
                                }
                                if (mode in listOf("Hold & aim", "Camera")) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(xSensitivity, { xSensitivity = it }, modifier = Modifier.weight(1f), label = { Text("X sensitivity") })
                                        OutlinedTextField(ySensitivity, { ySensitivity = it }, modifier = Modifier.weight(1f), label = { Text("Y sensitivity") })
                                    }
                                    OutlinedTextField(deadZone, { deadZone = it }, label = { Text("Dead zone [0, 1)") })
                                    if (mode == "Hold & aim") Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(invert, { invert = it }); Text("Invert Y") }
                                }
                                Text("Target preview (Apply before Save): ${ControllerBindings.label(code)} → $mode • ${point?.let { "(${it.first}, ${it.second})" } ?: "Target unmeasured"}", style = MaterialTheme.typography.titleSmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Button(onClick = { focus.clearFocus(); keyboard?.hide(); onPlaceTarget(); placing = true }, enabled = ready && overlay) { Text("Place Target") }
                                    OutlinedButton(onClick = { apply() }, enabled = point != null) { Text("Apply to draft") }
                                }
                                OutlinedButton(onClick = { targetPicker = true }, enabled = code != VisualMappingSession.RIGHT_STICK) { Text("Saved target") }
                                if (point != null) {
                                    TextButton(onClick = { coordinateX = point!!.first.toString(); coordinateY = point!!.second.toString(); coordinatesDialog = true }) { Text("Advanced coordinates") }
                                    Row {
                                        TextButton(onClick = { point = point!!.first.minus(1).coerceAtLeast(0f) to point!!.second }) { Text("← 1") }
                                        TextButton(onClick = { point = point!!.first.plus(1).coerceAtMost(profile.xRes - 1f) to point!!.second }) { Text("1 →") }
                                        TextButton(onClick = { point = point!!.first to point!!.second.minus(1).coerceAtLeast(0f) }) { Text("↑ 1") }
                                        TextButton(onClick = { point = point!!.first to point!!.second.plus(1).coerceAtMost(profile.yRes - 1f) }) { Text("1 ↓") }
                                    }
                                }
                                Row {
                                    TextButton(onClick = { confirmation = { try { session.replace(code, null); revision++; load(code) } catch (e: IllegalArgumentException) { error = e.message } } }, enabled = existing.isNotEmpty()) { Text("Remove") }
                                    TextButton(onClick = { session.resetBinding(code); revision++; load(code) }) { Text("Reset selected") }
                                }
                            }
                        }
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        if (!ready) Text("Mapper pause connection pending/unavailable. Target placement is disabled.", color = MaterialTheme.colorScheme.error)
                        Text("Save changes only this profile. Imported presets are local customizations; export a backup before future re-imports.", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { error = onSave(); if (error == null) onClose() }, enabled = session.dirty) { Text("Save Profile") }
                            OutlinedButton(onClick = { confirmation = onClose }) { Text("Discard / Close") }
                            TextButton(onClick = { session.undo(); revision++; selected?.let(::load) }) { Text("Undo") }
                        }
                        TextButton(onClick = { session.reset(); revision++; selected?.let(::load) }) { Text("Reset to saved") }
                        Text("${rows.size} persisted mappings • no synthetic preview touches", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (targetPicker) CapturedTargetPicker(profile.xRes, profile.yRes, profile.packageName,
                onSelect = { targetProposal = it; targetPicker = false }, onClose = { targetPicker = false })
            targetProposal?.let { target -> AlertDialog(onDismissRequest = { targetProposal = null },
                title = { Text("Replace with ${target.name}?") },
                text = { Text("${selected?.let { session.bindings(it).joinToString("\n") } ?: "Unmapped"}\n→ ${selected?.let { session.targetReplacement(it, target) }}\nAim tuning is retained for an aimed skill; fixed touches have no repeat. Other mappings stay unchanged. Save Profile commits this draft.") },
                confirmButton = { TextButton(onClick = {
                    try { selected?.let { session.applyTarget(it, target, true); revision++; load(it) }; targetProposal = null }
                    catch (e: Exception) { error = e.message; targetProposal = null }
                }) { Text("Replace in draft") } }, dismissButton = { TextButton(onClick = { targetProposal = null }) { Text("Cancel") } }) }
            if (chordEditor) ControllerChordEditor(session, onChanged = { revision++; selected?.let(::load) }, onClose = { chordEditor = false })
            if (coordinatesDialog) AlertDialog(onDismissRequest = { coordinatesDialog = false },
                title = { Text("Measured target coordinates") },
                text = { Column {
                    OutlinedTextField(coordinateX, { coordinateX = it }, label = { Text("X in profile plane") })
                    OutlinedTextField(coordinateY, { coordinateY = it }, label = { Text("Y in profile plane") })
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                } },
                confirmButton = { TextButton(onClick = {
                    val x = coordinateX.toFloatOrNull(); val y = coordinateY.toFloatOrNull()
                    if (x != null && y != null && x.isFinite() && y.isFinite() && x >= 0 && y >= 0 && x < profile.xRes && y < profile.yRes) {
                        point = x to y; coordinatesDialog = false; error = null
                    } else error = "Enter finite coordinates inside the saved profile plane."
                }) { Text("Use target") } },
                dismissButton = { TextButton(onClick = { coordinatesDialog = false }) { Text("Cancel") } })
            val closeDialog = closeRequested
            val action = confirmation
            if (closeDialog || action != null) AlertDialog(
                onDismissRequest = { confirmation = null; onKeepEditing() },
                title = { Text(if (closeDialog) "Discard unsaved changes?" else "Confirm mapping change") },
                text = { Text("Changes affect only this draft until Save Profile. Cancel keeps the current draft.") },
                confirmButton = { TextButton(onClick = { if (closeDialog) onClose() else action?.invoke(); confirmation = null }) { Text("Confirm") } },
                dismissButton = { TextButton(onClick = { confirmation = null; onKeepEditing() }) { Text("Cancel") } })
        }
    }
}

@Composable
private fun ControllerDiagram(session: VisualMappingSession, selected: String?, onSelect: (String) -> Unit) {
    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
      BoxWithConstraints(Modifier.width(720.dp).height(300.dp).testTag("controller-diagram")) {
        val width = maxWidth; val height = maxHeight
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(Color(0xFF35405A), topLeft = Offset(size.width * .04f, size.height * .04f), size = androidx.compose.ui.geometry.Size(size.width * .39f, size.height * .94f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(35f))
            drawRoundRect(Color(0xFF35405A), topLeft = Offset(size.width * .57f, size.height * .04f), size = androidx.compose.ui.geometry.Size(size.width * .39f, size.height * .94f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(35f))
            drawRoundRect(Color(0xFF171C28), topLeft = Offset(size.width * .43f, size.height * .3f), size = androidx.compose.ui.geometry.Size(size.width * .14f, size.height * .6f))
        }
        ControllerPresentation.controls.forEach { control ->
            val mapped = session.bindings(control.code).isNotEmpty()
            val label = "${control.label} • ${if (control.reserved) "native/reserved" else if (mapped) "mapped" else "unmapped"}"
            FilledTonalButton(onClick = { onSelect(control.code) }, enabled = !control.reserved,
                modifier = Modifier.offset(width * control.x - 25.dp, height * control.y - 24.dp).sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .testTag("control:${control.code}").semantics { contentDescription = label; stateDescription = if (selected == control.code) "Selected" else "Not selected" },
                contentPadding = PaddingValues(4.dp), colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (selected == control.code) MaterialTheme.colorScheme.primary else if (mapped) Color(0xFF574671) else Color(0xFF242C3C))) {
                Text(control.label)
            }
        }
    }
}
}
