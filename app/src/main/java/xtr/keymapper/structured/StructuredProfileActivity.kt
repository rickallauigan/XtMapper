package xtr.keymapper.structured

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import xtr.keymapper.keymap.KeymapProfiles
import java.util.concurrent.Executors

/** User-selected SAF files only. Parsing never activates mappings or accesses a root service. */
class StructuredProfileActivity:ComponentActivity() {
    private val worker=Executors.newSingleThreadExecutor()
    private var document by mutableStateOf<ProfileDocument?>(null)
    private var error by mutableStateOf<String?>(null)
    private var preview by mutableStateOf(false)
    private var targetName by mutableStateOf("")
    private var expected:Set<String>?=null
    private var exportText:String?=null
    private var generation=0L
    private var imported=false
    private var activationPending=false
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        intent.getStringExtra("profile")?.let {id->try{document=StructuredProfiles.read(this,id);targetName=id;expected=KeymapProfiles(this).sharedPref.getStringSet(id,null)?.toSet();activationPending=StructuredProfiles.pending(expected!!)}catch(e:Exception){error=e.message?.take(600)}}
        setContent {MaterialTheme(colorScheme=darkColorScheme(primary=androidx.compose.ui.graphics.Color(0xFFFF5477))) {Surface(Modifier.fillMaxSize()){Column(Modifier.safeDrawingPadding().padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("Profile files • JSON / YAML",style=MaterialTheme.typography.headlineSmall)
            Text("Imports do not activate a profile or change device associations. Legacy text/ZIP remain available in their existing editor.")
            Button(onClick={generation++;val i=Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").putExtra(Intent.EXTRA_MIME_TYPES,arrayOf("application/json","application/yaml","text/yaml","text/plain"));startActivityForResult(i,1)}){Text("Import JSON/YAML")}
            document?.let {d->
                Text("${d.profile.name} • ${d.profile.gamePackage}\n${d.profile.description}\n${d.profile.width} × ${d.profile.height} • ${d.bindings.size} bindings • ${d.bindings.count{!it.enabled}} disabled")
                Text(if(d.profile.enabled) "Profile enabled" else "Profile disabled")
                Text("Offsets are retained; target positions use the saved profile plane. Macro references do not include macro payloads; verify local availability before activation.")
                OutlinedTextField(targetName,{targetName=it;expected=KeymapProfiles(this@StructuredProfileActivity).sharedPref.getStringSet(it,null)?.toSet()},label={Text("Local profile name / duplicate name")})
                if(preview) {
                    Text("Imported files remain blocked from auto-selection until separately allowed.")
                    TextButton(onClick={targetName=(d.profile.name.take(70)+" copy");expected=KeymapProfiles(this@StructuredProfileActivity).sharedPref.getStringSet(targetName,null)?.toSet()}){Text("Duplicate under new name")}
                    Text(if(expected==null) "New profile" else "Existing profile conflict: Replace explicitly or choose a new name")
                    Button(onClick={try{StructuredProfiles.save(this@StructuredProfileActivity,targetName,d,expected,imported||activationPending);preview=false;activationPending=imported||activationPending;imported=false;document=StructuredProfiles.read(this@StructuredProfileActivity,targetName);expected=KeymapProfiles(this@StructuredProfileActivity).sharedPref.getStringSet(targetName,null)?.toSet();error=null}catch(e:Exception){error=e.message?.take(600)}}){Text(if(expected==null) "Confirm import / save" else "Confirm replace")}
                    TextButton(onClick={generation++;preview=false;document=null}){Text("Cancel import")}
                }
                if(activationPending&&!preview) Button(onClick={try{StructuredProfiles.save(this@StructuredProfileActivity,targetName,d,expected,false);activationPending=false;expected=KeymapProfiles(this@StructuredProfileActivity).sharedPref.getStringSet(targetName,null)?.toSet()}catch(e:Exception){error=e.message?.take(600)}}){Text("Explicitly allow profile selection")}
                Row {TextButton(onClick={export(false)}){Text("Export JSON")};TextButton(onClick={export(true)}){Text("Export YAML")}}
                Row {Checkbox(d.profile.enabled,{document=d.copy(profile=d.profile.copy(enabled=it));preview=true});Text("Profile enabled")}
                OutlinedTextField(d.profile.description,{document=d.copy(profile=d.profile.copy(description=it));preview=true},label={Text("Profile description")})
                d.bindings.forEachIndexed {index,b->
                    HorizontalDivider();Text("${b.displayInput()} • ${b.action}${if(b.enabled) "" else " • Disabled"}")
                    OutlinedTextField(b.name,{update(index,b.copy(name=it))},label={Text("Binding name")})
                    OutlinedTextField(b.description,{update(index,b.copy(description=it))},label={Text("Description")})
                    Row {Checkbox(b.enabled,{update(index,b.copy(enabled=it))});Text("Enabled • retains coordinates/tuning")}
                    Text(b.nativeLine,style=MaterialTheme.typography.bodySmall)
                }
                if(!preview) Button(onClick={preview=true}){Text("Review / save metadata")}
            }
            error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
            TextButton(onClick={finish()}){Text("Done / discard unsaved")}
        }}}}
    }
    private fun update(index:Int,b:ProfileBinding){document=document?.let{d->d.copy(bindings=d.bindings.toMutableList().also{it[index]=b})};preview=true}
    private fun export(asYaml:Boolean){try{exportText=ProfileCodec.encode(ProfileCodec.decode(ProfileCodec.encode(document ?: return)),asYaml);startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(if(asYaml)"application/yaml" else "application/json").putExtra(Intent.EXTRA_TITLE,"profile.${if(asYaml)"yaml" else "json"}"),2)}catch(e:Exception){error=e.message?.take(600)}}
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){super.onActivityResult(requestCode,resultCode,data);if(resultCode!=Activity.RESULT_OK)return;val uri=data?.data ?: return
        val ticket=generation;val output=exportText
        worker.execute {try{
            if(requestCode==1){val parsed=contentResolver.openInputStream(uri)?.use{ProfileCodec.decode(ProfileCodec.read(it))} ?: error("Cannot read file")
                runOnUiThread{if(!isDestroyed&&ticket==generation){document=parsed;imported=true;targetName=parsed.profile.name;expected=KeymapProfiles(this).sharedPref.getStringSet(targetName,null)?.toSet();preview=true;error=null}}
            }else if(requestCode==2){val text=output ?: return@execute;contentResolver.openOutputStream(uri,"wt")?.use{it.write(text.toByteArray(Charsets.UTF_8))} ?: error("Cannot write selected document")}
        }catch(e:Exception){runOnUiThread{if(!isDestroyed&&ticket==generation)error=e.message?.take(600) ?: "File operation failed"}}}
    }
    override fun onDestroy(){generation++;worker.shutdownNow();super.onDestroy()}
}
