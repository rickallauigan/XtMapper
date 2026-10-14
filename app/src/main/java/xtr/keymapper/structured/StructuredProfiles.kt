package xtr.keymapper.structured

import android.content.Context
import android.util.Base64
import xtr.keymapper.keymap.ProfileConfiguration
import xtr.keymapper.keymap.KeymapProfiles

/** One string-set transaction holds runtime lines and their versioned source document.
 * Old readers ignore the metadata tag; current readers verify it against the runtime projection.
 */
object StructuredProfiles {
    const val TAG="STRUCTURED_PROFILE_V1"
    @JvmStatic fun contains(text:String)=text.lineSequence().any{it.startsWith("$TAG ")}
    fun pack(d:ProfileDocument,pending:Boolean=false):Set<String> {
        val json=ProfileCodec.encode(d)
        val checked=ProfileCodec.decode(json)
        return projection(checked,pending)+"$TAG ${if(pending) "PENDING " else ""}${Base64.encodeToString(json.toByteArray(Charsets.UTF_8),Base64.NO_WRAP)}"
    }
    @JvmStatic fun document(lines:Set<String>):ProfileDocument? {
        val meta=lines.filter{it.startsWith("$TAG ")};if(meta.isEmpty())return null
        require(meta.size==1){"storage: duplicate structured metadata"}
        val pending=meta.single().startsWith("$TAG PENDING ");val encoded=meta.single().substringAfter(' ').removePrefix("PENDING ");require(encoded.length<=2*ProfileCodec.MAX_BYTES)
        val d=ProfileCodec.decode(ProfileCodec.read(java.io.ByteArrayInputStream(Base64.decode(encoded,Base64.DEFAULT))))
        require(ProfileConfiguration.validate((lines-meta.toSet()).joinToString("\n"))==projection(d,pending)){"storage: metadata/runtime conflict; nothing overwritten"}
        return d
    }
    @JvmStatic fun validateContainer(text:String):Set<String> {
        val lines=text.lineSequence().filter{it.isNotBlank()}.map{it.trim()}.toSet()
        require(document(lines)!=null);return lines
    }
    @JvmStatic fun runtime(lines:Set<String>):Set<String> = document(lines)?.let{projection(it,pending(lines))} ?: lines
    @JvmStatic fun blocked(lines:Set<String>):Boolean = document(lines)?.let{!it.profile.enabled||pending(lines)} ?: false
    @JvmStatic fun pending(lines:Set<String>)=lines.any{it.startsWith("$TAG PENDING ")}
    private fun projection(d:ProfileDocument,pending:Boolean)=if(pending)d.copy(profile=d.profile.copy(enabled=false)).runtimeLines() else d.runtimeLines()
    fun read(context:Context,id:String):ProfileDocument {
        val lines=KeymapProfiles(context).sharedPref.getStringSet(id,null)?.toSet() ?: error("Profile deleted")
        return (document(lines) ?: ProfileCodec.fromLegacy(id,lines)).let{it.copy(profile=it.profile.copy(name=id))}
    }
    /** Merge only the editor's active projection; disabled definitions and labels stay intact. */
    @JvmStatic fun reconcile(old:Set<String>?,edited:Set<String>):Set<String> {
        val d=old?.let{document(it)} ?: return ProfileConfiguration.validate(edited.filterNot{it.startsWith("$TAG ")}.joinToString("\n"))
        val native=edited.filterNot{it.startsWith("$TAG ")}.toSet()
        val fresh=ProfileCodec.fromLegacy(d.profile.name,native)
        val disabled=d.bindings.filterNot{it.enabled}
        val active=fresh.bindings.map{b ->d.bindings.firstOrNull{it.identity==b.identity}?.let{prior->require(prior.enabled){"Editor conflicts with a disabled binding; enable it explicitly first"};b.copy(name=prior.name,description=prior.description)} ?: b}
        // Rescale retained disabled configurations through the same existing runtime model.
        val retained=if(d.profile.width==fresh.profile.width&&d.profile.height==fresh.profile.height)disabled else disabled.map{b->
            val p=ProfileConfiguration.parse("SCREENSIZE ${d.profile.width} ${d.profile.height}\n${b.nativeLine}")
            p.scale(fresh.profile.width.toFloat(),fresh.profile.height.toFloat())
            val line=when(b.action){"fixed_touch"->if(b.input=="MOUSE_LEFT") "MOUSE_LEFT ${p.leftClick.x} ${p.leftClick.y}" else if(b.input=="MOUSE_RIGHT") "MOUSE_RIGHT ${p.rightClick.x} ${p.rightClick.y}" else p.keys.single().let { "${it.code} ${it.x} ${it.y} ${it.offset}" + if(it.repeatIntervalMs>0) " ${it.repeatIntervalMs}" else "" }
                "chord"->p.chords.single().data;"stick_aim","mouse_skill_aim"->p.aimKeys.single().data;"camera","stick_camera"->p.camera.data
                "movement_pad"->p.dpadArray.first{it!=null}.data;"mouse_walk"->p.mouseWalk.let { "MOUSE_WALK ${it.x} ${it.y} ${it.radius}" };"mouse_aim"->p.mouseAimConfig.data;"swipe"->p.swipeKeys.single().data;else->b.nativeLine}
            MappingFields.fromLine(line,b.name,b.description,false)
        }
        return pack(ProfileDocument(fresh.profile.copy(description=d.profile.description,enabled=if(pending(old!!))d.profile.enabled else fresh.profile.enabled),active+retained).validate(),pending(old!!))
    }
    fun save(context:Context,id:String,d:ProfileDocument,expected:Set<String>?,pending:Boolean=false) {
        require(id.isNotBlank()&&id.length<=80&&id.none{it.isISOControl()||it=='/'||it=='\\'}&&id!="."&&id!=".."){"profile.name: invalid local name"}
        val prefs=KeymapProfiles(context).sharedPref;val next=pack(d.copy(profile=d.profile.copy(name=id)),pending)
        synchronized(SAVE_LOCK){require(prefs.getStringSet(id,null)?.toSet()==expected){"Profile changed elsewhere; reopen preview"}
            writeSet(prefs,id,next)}
    }
    @JvmStatic fun writeSet(prefs:android.content.SharedPreferences,id:String,next:Set<String>) {
        synchronized(SAVE_LOCK) {
            val prior=prefs.getStringSet(id,null)?.toSet()
            if(!prefs.edit().putStringSet(id,next.toSet()).commit()) {
                val rollback=prefs.edit();if(prior==null)rollback.remove(id)else rollback.putStringSet(id,prior)
                rollback.commit() // Restore memory too; SharedPreferences may update memory on failed disk commit.
                error("Profile save failed; prior state restored in memory. Verify storage before retrying.")
            }
        }
    }
    @JvmField val SAVE_LOCK=Any()
}
