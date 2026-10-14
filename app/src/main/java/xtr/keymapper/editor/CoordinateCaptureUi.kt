package xtr.keymapper.editor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.util.UUID
import kotlin.math.roundToInt

@Composable
fun CoordinateCaptureUi(session: CoordinateCaptureSession, profileId: String?, gamePackage: String?,
    overlay: Boolean, ready: Boolean, closeRequested: Boolean, onKeepEditing: () -> Unit,
    onClose: () -> Unit, onOpenGame: () -> Unit) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val store = remember { CapturedTargetStore(context) }
    var targets by remember { mutableStateOf(emptyList<CapturedTarget>()) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { try { targets = store.read() } catch (_: Exception) { error = "Target library unreadable. No stored data was changed." } }
    var revision by remember { mutableIntStateOf(0) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var paletteSize by remember { mutableStateOf(IntSize.Zero) }
    var paletteOffset by remember { mutableStateOf(Offset.Zero) }
    var editing by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("DPAD_UP") }
    var trace by remember { mutableStateOf(false) }
    var library by remember { mutableStateOf(false) }
    var confirmClose by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<CapturedTarget?>(null) }
    fun change(block: () -> Unit) { try { block(); error = null; revision++ } catch (e: Exception) { error = e.message ?: "Operation failed" } }
    fun copy(text: String) { context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Captured coordinates", text)) }
    fun current(): CapturedTarget {
        val p = session.point ?: error("Tap a real target first")
        return CapturedTarget(editing ?: UUID.randomUUID().toString(), name.trim(), description, p.first, p.second,
            session.width.toInt(), session.height.toInt(), System.currentTimeMillis(), profileId ?: targets.firstOrNull {it.id==editing}?.profileId, gamePackage ?: targets.firstOrNull {it.id==editing}?.gamePackage, input, size.width, size.height)
    }
    // Each explicit Save/Remove commits one complete validated library; Cancel never writes.
    fun save() = change {
        val target = current(); val next = targets.filterNot { it.id == target.id } + target
        store.save(targets, next); targets = next; editing = target.id
        session.stop()
    }
    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFFF5477))) {
        Box(Modifier.fillMaxSize().onSizeChanged { size = it }) {
            val plane = if (size.width > 0 && size.height > 0) TargetPlane(size.width.toFloat(), size.height.toFloat(),
                profileWidth = session.width, profileHeight = session.height) else null
            val selected = remember(revision) { session.selection }
            Canvas(Modifier.fillMaxSize().testTag("capture-plane").semantics { contentDescription = "Coordinate capture surface; touches do not reach the game" }
                .pointerInput(plane) {
                    if (plane != null) awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        val p = plane.toProfile(down.position.x, down.position.y)
                        session.begin(p.first, p.second); revision++
                        var ended = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                                val primary = event.changes.firstOrNull { it.id == down.id }
                                if (primary == null || !primary.pressed) {
                                    primary?.let { val up = plane.toProfile(it.position.x, it.position.y); session.move(up.first, up.second) }
                                    session.end(); ended = true; revision++; break
                                }
                                val next = plane.toProfile(primary.position.x, primary.position.y)
                                session.move(next.first, next.second); revision++
                            }
                        } finally { if (!ended) { session.cancelGesture(); revision++ } }
                    }
                }) {
                if (!session.capturing) drawRect(Color.Black.copy(alpha = .16f))
                if (plane != null) {
                    selected.trace.zipWithNext().forEach { (a,b) ->
                        val av = plane.toViewport(a.first,a.second); val bv = plane.toViewport(b.first,b.second)
                        drawLine(Color.Cyan, Offset(av.first,av.second),Offset(bv.first,bv.second), 2.dp.toPx())
                    }
                    selected.point?.let {
                        val p = plane.toViewport(it.first,it.second); val c = Offset(p.first,p.second)
                        drawCircle(Color(0xFFFF5477), 10.dp.toPx(), c, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                        drawLine(Color.White,c-Offset(24.dp.toPx(),0f),c+Offset(24.dp.toPx(),0f),2.dp.toPx())
                        drawLine(Color.White,c-Offset(0f,24.dp.toPx()),c+Offset(0f,24.dp.toPx()),2.dp.toPx())
                    }
                }
            }
            val boundedOffset = Offset(paletteOffset.x.coerceIn(0f, (size.width-paletteSize.width).coerceAtLeast(0).toFloat()),
                paletteOffset.y.coerceIn(0f,(size.height-paletteSize.height).coerceAtLeast(0).toFloat()))
            Surface(Modifier.offset { IntOffset(boundedOffset.x.roundToInt(),boundedOffset.y.roundToInt()) }
                .padding(8.dp).safeDrawingPadding().widthIn(max=360.dp).fillMaxHeight(.8f).onSizeChanged { paletteSize=it },
                shape=MaterialTheme.shapes.large,tonalElevation=8.dp) {
                Column(Modifier.padding(12.dp)) {
                    Text("Coordinate Capture • drag this header", modifier=Modifier.fillMaxWidth().testTag("capture-palette-handle")
                        .pointerInput(Unit) { detectDragGestures { c, delta -> c.consume(); paletteOffset += delta } }, style=MaterialTheme.typography.titleMedium)
                    Text("${session.width.toInt()} × ${session.height.toInt()} • origin 0,0")
                    Text(selected.point?.let { "X ${it.first}   Y ${it.second}" } ?: "Tap or drag to select", modifier=Modifier.testTag("capture-readout"), style=MaterialTheme.typography.titleLarge)
                    Row {
                        Button(onClick={ focus.clearFocus(); keyboard?.hide(); change { session.start() } },enabled=ready&&!session.capturing&&(size.width > size.height)==(session.width > session.height)) { Text("Start Capture") }
                        TextButton(onClick={ change { session.stop() } },enabled=session.capturing) { Text("Stop Capture") }
                    }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    if (!overlay) TextButton(onClick={context.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${context.packageName}")))}) { Text("Grant overlay permission; reopen capture afterward") }
                    if (!overlay) Text("Fullscreen fallback: game HUD is not visible. These are manual measurements, not live game calibration.")
                    Text(if(session.capturing) "Capture active: all screen touches are intercepted." else "Capture stopped. Close to return touches to the game.")
                    if ((size.width > size.height)!=(session.width > session.height)) Text("Rotate to match the profile orientation before capture.",color=MaterialTheme.colorScheme.error)
                    if(!ready) Text("Waiting for mapper pause; capture disabled.",color=MaterialTheme.colorScheme.error)
                    if (overlay && !gamePackage.isNullOrEmpty()) TextButton(onClick=onOpenGame) { Text("Open game behind overlay") }
                    Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(trace,{ trace=it; session.tracing=it }); Text("Touch movement trace") }
                    listOf(1,10).forEach { step ->
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
                            listOf("←" to (-step to 0),"→" to (step to 0),"↑" to (0 to -step),"↓" to (0 to step)).forEach { (label,d) ->
                                TextButton(onClick={ change { session.nudge(d.first,d.second) } },enabled=selected.point!=null,
                                    modifier=Modifier.semantics { contentDescription="Nudge $label $step pixels" }) { Text("$label $step") }
                            }
                        }
                    }
                    Row { TextButton(onClick={ change { session.undo() } }) { Text("Undo") }
                        TextButton(onClick={ change { session.reset() }; editing=null; name=""; description="" }) { Text("Reset") } }
                    OutlinedTextField(name,{name=it},label={Text("Target name")},singleLine=true,modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(description,{description=it},label={Text("Description / HUD notes")},modifier=Modifier.fillMaxWidth())
                    Text("Snippet input • does not bind or activate a profile")
                    Row(Modifier.fillMaxWidth().heightIn(max=140.dp).verticalScroll(rememberScrollState())) {
                        Column { ControllerPresentation.controls.filter { !it.reserved && it.code != VisualMappingSession.RIGHT_STICK }.chunked(3).forEach { row ->
                            Row { row.forEach { c -> FilterChip(selected=input==c.code,onClick={input=c.code},label={Text(c.label)}) } }
                        } }
                    }
                    Text("JSON/YAML snippets are documentation only; structured import (#24) is not implemented.",style=MaterialTheme.typography.bodySmall)
                    Row { TextButton(onClick={ selected.point?.let { copy("${it.first}, ${it.second}") } },enabled=selected.point!=null) { Text("Copy X/Y") }
                        TextButton(onClick={ change { copy(current().snippetJson()) } }) { Text("Copy JSON") }
                        TextButton(onClick={ change { copy(current().snippetYaml()) } }) { Text("Copy YAML") } }
                    TextButton(onClick={change {copy(current().json().toString(2))}}) {Text("Copy measurement record") }
                    Button(onClick={save()},enabled=selected.point!=null) { Text(if(editing==null) "Save target" else "Save target changes") }
                    TextButton(onClick={change {session.stop();session.reset()};editing=null;name="";description=""}) {Text("New target") }
                    TextButton(onClick={session.stop(); revision++; library=true}) { Text("Saved targets (${targets.size})") }
                    Text("Saved locally only after Save target. Remove targets individually; no touch log is saved.",style=MaterialTheme.typography.bodySmall)
                    error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
                    TextButton(onClick={session.stop();revision++;confirmClose=true}) { Text("Cancel / Done") }
                    }
                }
            }
            if(library) AlertDialog(onDismissRequest={library=false},title={Text("Captured Targets")},text={
                Column(Modifier.heightIn(max=350.dp).verticalScroll(rememberScrollState())) {
                    targets.forEach { t ->
                        Text("${t.name} • ${t.coordinates()} • ${t.width} × ${t.height}")
                        Text(t.description,style=MaterialTheme.typography.bodySmall)
                        Row { TextButton(onClick={change { val p=t.pointFor(session.width.toInt(),session.height.toInt(),gamePackage ?: t.gamePackage ?: "")
                            session.select(p.first,p.second); editing=t.id; name=t.name; description=t.description; input=t.input; library=false }}) {Text("Edit")}
                            TextButton(onClick={copy(t.snippetJson())}) {Text("Copy JSON")}
                            TextButton(onClick={deleting=t}) {Text("Remove")} }
                    }
                }
            },confirmButton={TextButton(onClick={library=false}) {Text("Close")}})
            deleting?.let { t -> AlertDialog(onDismissRequest={deleting=null},title={Text("Remove ${t.name}?")},
                confirmButton={TextButton(onClick={change {val next=targets-t;store.save(targets,next);targets=next};deleting=null}) {Text("Remove")}},
                dismissButton={TextButton(onClick={deleting=null}) {Text("Cancel")}}) }
            if(confirmClose||closeRequested) AlertDialog(onDismissRequest={confirmClose=false;onKeepEditing()},title={Text("Close capture?")},
                text={Text("Unsaved coordinates and edits are discarded. Explicitly saved targets remain in your local library.")},
                confirmButton={TextButton(onClick={session.stop();onClose()}) {Text("Discard unsaved / Close")}},
                dismissButton={TextButton(onClick={confirmClose=false;onKeepEditing()}) {Text("Keep editing")}})
        }
    }
}

@Composable
fun CapturedTargetPicker(width: Int, height: Int, gamePackage: String, onSelect: (CapturedTarget) -> Unit, onClose: () -> Unit) {
    val context=LocalContext.current
    var targets by remember { mutableStateOf(emptyList<CapturedTarget>()) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { try {targets=CapturedTargetStore(context).read()} catch (_: Exception) {error="Target library unreadable"} }
    AlertDialog(onDismissRequest=onClose,title={Text("Choose a measured target")},text={
        Column(Modifier.heightIn(max=350.dp).verticalScroll(rememberScrollState())) {
            Text("Only targets from this game and profile plane can be applied.")
            error?.let {Text(it)}
            targets.forEach {t ->
                val compatible=t.width==width&&t.height==height&&(t.gamePackage.isNullOrEmpty()||t.gamePackage==gamePackage)
                TextButton(enabled=compatible,onClick={onSelect(t)}) {Text("${t.name} • ${t.coordinates()}${if(!compatible) " • other game/plane" else ""}")}
            }
            if(targets.isEmpty()) Text("No saved targets. Open Coordinate Capture first.")
        }
    },confirmButton={TextButton(onClick=onClose){Text("Cancel")}})
}
