package xtr.keymapper.structured

import xtr.keymapper.keymap.ProfileConfiguration
import xtr.keymapper.controller.ControllerBindings

/** Immutable metadata plus a validated mapping in the existing native type system. */
data class ProfileHeader(val name:String,val description:String,val gamePackage:String,val enabled:Boolean,val width:Int,val height:Int)
data class ProfileBinding(val action:String,val input:String,val name:String,val description:String,val enabled:Boolean,val nativeLine:String) {
    fun displayInput():String {
        fun label(code:String)=when(code){"RIGHT_STICK"->"Right stick";"MOUSE_LEFT","BTN_MOUSE"->"Mouse Left";"MOUSE_RIGHT","BTN_RIGHT"->"Mouse Right";else->ControllerBindings.label(code)}
        return if(action=="chord") label(nativeLine.split(Regex("\\s+"))[1])+" + "+label(input) else input.split('+').joinToString(" / "){label(it)}
    }
    val identity get() = "$action:$input"
}
data class ProfileDocument(val profile:ProfileHeader,val bindings:List<ProfileBinding>) {
    fun runtimeLines():Set<String> = ProfileConfiguration.validate((listOf("APPLICATION ${profile.gamePackage}","SCREENSIZE ${profile.width} ${profile.height}")+
        (if(profile.enabled) listOf("ENABLED") else emptyList())+bindings.filter{it.enabled}.map{it.nativeLine}).joinToString("\n"))
    fun validate():ProfileDocument {
        require(bindings.size<=200){"bindings: maximum 200"}
        require(bindings.map{it.identity}.distinct().size==bindings.size){"bindings: duplicate action/input"}
        // Validate all definitions even when disabled. No hidden malformed/chord-conflicting state.
        ProfileConfiguration.validate((listOf("APPLICATION ${profile.gamePackage}","SCREENSIZE ${profile.width} ${profile.height}")+bindings.map{it.nativeLine}).joinToString("\n"))
        val triggers=bindings.flatMap { b-> val d=b.nativeLine.split(Regex("\\s+"));when(b.action){
            "fixed_touch","stick_aim","mouse_skill_aim","macro","camera"->listOf(b.input)
            "swipe"->listOf(ControllerBindings.editorCode(d[1]),ControllerBindings.editorCode(d[4]))
            "movement_pad"->d.drop(8).map{ControllerBindings.editorCode(it)}
            else->emptyList()
        }}.map {when(it){"MOUSE_LEFT"->"BTN_MOUSE";"MOUSE_RIGHT"->"BTN_RIGHT";else->it}}
        require(triggers.distinct().size==triggers.size){"bindings: duplicate input ownership"}
        return this
    }
}

/** Explicit action field tables, not a raw-line escape hatch in the file format. */
internal object MappingFields {
    data class Layout(val tag:String,val fields:List<String>)
    private fun layout(tag:String,fields:String)=Layout(tag,fields.split(" "))
    val layouts=linkedMapOf(
        "stick_aim" to layout("STICK_AIM","input position.x position.y aim.radius aim.sensitivity.x aim.sensitivity.y aim.dead_zone aim.invert_y"),
        "mouse_skill_aim" to layout("AIM_KEY","input position.x position.y aim.radius aim.sensitivity.x aim.sensitivity.y"),
        "stick_camera" to layout("STICK_CAMERA","position.x position.y sensitivity.x sensitivity.y dead_zone"),
        "camera" to layout("CAMERA","position.x position.y sensitivity.x sensitivity.y toggle input auto_active"),
        "chord" to layout("CHORD","modifier input position.x position.y offset"),
        "movement_pad" to layout("DPAD_UDLR","editor_position.x editor_position.y radius position.x position.y size.width size.height directions.up directions.down directions.left directions.right"),
        "swipe" to layout("SWIPE_KEY","input position.x position.y end_input end_position.x end_position.y"),
        "macro" to layout("MACRO","macro_id input"),
        "mouse_walk" to layout("MOUSE_WALK","position.x position.y radius"),
        "mouse_aim" to layout("MOUSE_AIM","position.x position.y limited_bounds size.width size.height click_position.x click_position.y sensitivity.x sensitivity.y nonlinear")
    )
    val booleans=setOf("aim.invert_y","auto_active","limited_bounds","nonlinear")
    val identities=setOf("input","modifier","end_input","directions.up","directions.down","directions.left","directions.right")
    fun identify(line:String):Pair<String,String> {
        val d=line.split(Regex("\\s+"));val tag=d[0]
        if(ControllerBindings.isBinding(tag)||tag in setOf("MOUSE_LEFT","MOUSE_RIGHT")) return "fixed_touch" to ControllerBindings.canonical(tag)
        val action=layouts.entries.firstOrNull{it.value.tag==tag || it.key=="movement_pad"&&tag=="DPAD"}?.key ?: error("Unsupported native mapping $tag")
        val l=layouts.getValue(action);val i=l.fields.indexOf("input")
        val input=if(i>=0) (if(d[i+1] in setOf("BTN_RIGHT","BTN_MOUSE")) d[i+1] else ControllerBindings.editorCode(d[i+1])) else when(action){"stick_camera"->"RIGHT_STICK";"movement_pad"->d.drop(8).joinToString("+");else->tag}
        return action to input
    }
    fun fromLine(line:String,name:String="",description:String="",enabled:Boolean=true):ProfileBinding {
        val (action,input)=identify(line)
        val d=line.split(Regex("\\s+"))
        val normalized=when(d[0]) {
            "CAMERA" -> if(d.size==7) "$line 0" else line
            "MOUSE_AIM" -> if(d.size==8) "$line 1 1 0" else line
            else -> line
        }
        return ProfileBinding(action,input,name.ifEmpty{input},description,enabled,normalized)
    }
    fun map(binding:ProfileBinding):Map<String,Any> {
        val out=linkedMapOf<String,Any>("input" to binding.input,"name" to binding.name,"description" to binding.description,"action" to binding.action,"isEnabled" to binding.enabled)
        val d=binding.nativeLine.split(Regex("\\s+"))
        val fields=if(binding.action=="fixed_touch") listOf("position.x","position.y")+(if(d[0].startsWith("MOUSE_")) emptyList() else listOf("offset"))+(if(d.size==5) listOf("repeat_ms") else emptyList()) else layouts.getValue(binding.action).fields
        fields.forEachIndexed {i,path ->
            val v=d[i+1]
            val value:Any=when {path in booleans ->v!="0";path=="toggle"->v.toInt()!=0;path in identities->if(v in setOf("BTN_RIGHT","BTN_MOUSE")) v else ControllerBindings.editorCode(v);path=="macro_id"->v;else->v.toDouble()}
            put(out,path,value)
        }
        return out
    }
    @Suppress("UNCHECKED_CAST")
    fun put(out:MutableMap<String,Any>,path:String,value:Any) {
        val keys=path.split('.');var current=out
        keys.dropLast(1).forEach {key->current=current.getOrPut(key){linkedMapOf<String,Any>()} as MutableMap<String,Any>}
        current[keys.last()]=value
    }
}
