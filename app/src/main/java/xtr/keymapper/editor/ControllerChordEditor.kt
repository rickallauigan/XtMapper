package xtr.keymapper.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import xtr.keymapper.controller.ControllerBindings

/** Edits the existing CHORD grammar only; precedence/ownership stay in the merged runtime. */
@Composable
fun ControllerChordEditor(session: VisualMappingSession, onChanged: () -> Unit, onClose: () -> Unit) {
    var modifier by remember { mutableStateOf("BTN_SELECT") }
    var trigger by remember { mutableStateOf("BTN_TR") }
    var point by remember { mutableStateOf<Pair<Float,Float>?>(null) }
    var picker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirming by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    val p=remember(revision) {session.profile}
    fun edit(m: String,t: String) {
        modifier=m;trigger=t;error=null
        val old=p.chords.firstOrNull {it.modifier==m&&it.trigger==t}
        point=old?.let {(it.x+it.offset) to (it.y+it.offset)}
    }
    AlertDialog(onDismissRequest=onClose,title={Text("Controller chords")},text={
        Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState())) {
            Text("Modifier first activates the chord. Trigger first keeps its ordinary action. Releasing the modifier first never transfers ownership.")
            Text("A modifier cannot also have a standalone mapping. Remove that binding explicitly in the main editor only if you want this variation; Undo restores it.")
            p.chords.forEach {c -> TextButton(onClick={edit(c.modifier,c.trigger)}) {Text("${c.label()} • ${c.x+c.offset}, ${c.y+c.offset}")} }
            Text("Modifier")
            ControllerPresentation.controls.filter {!it.reserved&&ControllerBindings.isController(it.code)}.chunked(3).forEach {row -> Row {
                row.forEach {c -> FilterChip(selected=modifier==c.code,onClick={edit(c.code,trigger)},label={Text(ControllerBindings.label(c.code))})}
            } }
            Text("Trigger")
            ControllerPresentation.controls.filter {!it.reserved&&ControllerBindings.isController(it.code)}.chunked(3).forEach {row -> Row {
                row.forEach {c -> FilterChip(selected=trigger==c.code,onClick={edit(modifier,c.code)},label={Text(ControllerBindings.label(c.code))})}
            } }
            Text(point?.let {"Measured target ${it.first}, ${it.second}"} ?: "Choose a saved measured target")
            TextButton(onClick={picker=true}) {Text("Choose saved target")}
            Button(onClick={confirming=true},enabled=point!=null) {Text("Apply chord to draft")}
            TextButton(onClick={try {session.chord(modifier,trigger,null,null);revision++;onChanged();point=null} catch(e: Exception) {error=e.message}}) {Text("Remove selected chord from draft")}
            error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
            Text("All changes remain a draft until Save Profile.")
        }
    },confirmButton={TextButton(onClick=onClose) {Text("Done")}})
    if(picker) CapturedTargetPicker(p.xRes,p.yRes,p.packageName,onSelect={point=it.pointFor(p.xRes,p.yRes,p.packageName);picker=false},onClose={picker=false})
    if(confirming) AlertDialog(onDismissRequest={confirming=false},title={Text("Apply ${ControllerBindings.label(modifier)} + ${ControllerBindings.label(trigger)}?")},
        text={Text("Target ${point?.first}, ${point?.second}. This suppresses the trigger's base action when the modifier is held first. Standalone-modifier conflicts are rejected.")},
        confirmButton={TextButton(onClick={try {point?.let {session.chord(modifier,trigger,it.first,it.second)};revision++;onChanged();error=null}
            catch(e:Exception) {error=e.message};confirming=false}) {Text("Confirm")}},dismissButton={TextButton(onClick={confirming=false}) {Text("Cancel")}})
}
