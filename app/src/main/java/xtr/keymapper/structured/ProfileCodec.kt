package xtr.keymapper.structured

import org.json.JSONObject
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.nodes.*
import java.io.InputStream
import xtr.keymapper.controller.ControllerBindings

object ProfileCodec {
    const val MAX_BYTES=1_048_576
    fun read(stream:InputStream):String {
        val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);while(true){val count=stream.read(buffer);if(count<0)break;require(out.size()+count<=MAX_BYTES){"file: exceeds 1 MiB"};out.write(buffer,0,count)};val bytes=out.toByteArray();require(bytes.size<=MAX_BYTES){"file: exceeds 1 MiB"}
        return java.nio.charset.StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()
    }
    fun decode(text:String):ProfileDocument {
        require(text.toByteArray(Charsets.UTF_8).size<=MAX_BYTES){"file: exceeds 1 MiB"}
        val root=if(text.trimStart().startsWith("{")) StrictJson(text).read() else yamlValue(text)
        val r=obj(root,"root");keys(r,setOf("schema_version","profile","bindings","camera"),"root")
        require(number(r["schema_version"],"schema_version")==1.0){"schema_version: unsupported version"}
        val p=obj(r["profile"],"profile");keys(p,setOf("name","description","game_package","isEnabled","screen"),"profile")
        val s=obj(p["screen"],"profile.screen");keys(s,setOf("width","height"),"profile.screen")
        val h=ProfileHeader(string(p["name"],"profile.name",80),optionalString(p,"description",1000),string(p["game_package"],"profile.game_package",200),bool(p,"isEnabled"),integer(s["width"],"profile.screen.width"),integer(s["height"],"profile.screen.height"))
        require(h.width in 1..32768&&h.height in 1..32768){"profile.screen: dimensions must be 1..32768"}
        require(h.gamePackage.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+"))){"profile.game_package: invalid package"}
        val raw=r["bindings"];require(raw is List<*> && raw.size<=200){"bindings: expected array, maximum 200"}
        val bindings=raw.mapIndexed{i,b->binding(obj(b,"bindings[$i]"),"bindings[$i]",h)}.toMutableList()
        r["camera"]?.let{ val c=obj(it,"camera");bindings+=binding(if(c["action"]=="stick_camera"&&!c.containsKey("input")) c+mapOf("input" to "RIGHT_STICK") else c,"camera",h)}
        return ProfileDocument(h,bindings).validate()
    }
    private fun binding(m:Map<String,Any?>,path:String,h:ProfileHeader):ProfileBinding {
        val action=string(m["action"],"$path.action",40)
        val input=string(m["input"],"$path.input",200).let{ControllerBindings.canonical(it)}
        val fields=if(action=="fixed_touch") listOf("position.x","position.y","offset","repeat_ms") else MappingFields.layouts[action]?.fields ?: error("$path.action: unsupported action")
        val allowed=setOf("input","name","description","action","isEnabled")+fields.map{it.substringBefore('.')}
        keys(m,allowed,path)
        fun field(key:String):Any? {var value:Any?=m;key.split('.').forEach{value=obj(value,"$path.$key")[it]};return value}
        // Validate nested objects too: mixed/unknown representations are rejected.
        fields.filter{it.contains('.')}.groupBy{it.substringBefore('.')}.forEach{(parent,paths)->
            val o=obj(m[parent],"$path.$parent");keys(o,paths.map{it.substringAfter('.').substringBefore('.')}.toSet(),"$path.$parent")
            paths.filter{it.count{c->c=='.'}==2}.groupBy{it.split('.')[1]}.forEach{(sub,pp)->keys(obj(o[sub],"$path.$parent.$sub"),pp.map{it.substringAfterLast('.')}.toSet(),"$path.$parent.$sub")}
        }
        fun token(key:String):String {
            val v=field(key) ?: when(key){"offset"->0;"repeat_ms"->return "";else->error("$path.$key: required")}
            return when {
                key in MappingFields.booleans||key=="toggle"->{require(v is Boolean){"$path.$key: expected boolean"};if(v) "1" else "0"}
                key in MappingFields.identities->{val code=string(v,"$path.$key",100).let { if(it in setOf("BTN_RIGHT","BTN_MOUSE")) it else ControllerBindings.editorCode(it) };require(ControllerBindings.isBinding(code)||code in setOf("BTN_RIGHT","BTN_MOUSE")){"$path.$key: invalid input"};code}
                key=="macro_id"->{val id=string(v,"$path.$key",100);require(id.matches(Regex("[A-Za-z0-9_-]+(\\.json)?"))){"$path.macro_id: local ID only, no paths"};id}
                else->{val n=number(v,"$path.$key");if(key in setOf("radius","aim.radius","sensitivity.x","sensitivity.y","aim.sensitivity.x","aim.sensitivity.y","size.width","size.height"))require(n>0){"$path.$key: must be positive"}
                    if(key in setOf("dead_zone","aim.dead_zone"))require(n>=0&&n<1){"$path.$key: must be in [0,1)"}
                    require(n.toFloat().isFinite()){ "$path.$key: outside runtime numeric range" }
                    if(key=="repeat_ms" || action=="movement_pad" && key in setOf("size.width","size.height")){require(n==n.toInt().toDouble()){ "$path.$key: expected integer" };n.toInt().toString()}else n.toString()}
            }
        }
        val line=if(action=="fixed_touch") {
            require(ControllerBindings.isBinding(input)||input in setOf("MOUSE_LEFT","MOUSE_RIGHT")){"$path.input: invalid fixed input"}
            val x=token("position.x");val y=token("position.y");val offset=token("offset");val repeat=token("repeat_ms")
            if(input.startsWith("MOUSE_")){require(offset.toDouble()==0.0&&repeat.isEmpty()){ "$path: mouse fixed touch cannot repeat/offset" };"$input $x $y"}
            else "$input $x $y $offset${if(repeat.isEmpty()) "" else " $repeat"}"
        } else {val l=MappingFields.layouts.getValue(action);l.tag+" "+l.fields.joinToString(" "){token(it)}}
        val b=MappingFields.fromLine(line,optionalString(m,"name",80).ifEmpty{input},optionalString(m,"description",1000),bool(m,"isEnabled"))
        require(input==b.input){"$path.input: does not match action identity (${b.input})"}
        // Bound touch positions, never clamp imported values silently. Editor origins may be negative.
        fields.filter{it.endsWith("position.x")||it.endsWith("position.y")}.filterNot{it.startsWith("editor_")}.forEach{key->
            val n=number(field(key),"$path.$key");val effective=n+if(action in setOf("fixed_touch","chord")) token("offset").toDouble() else 0.0;val actual=n.toFloat()+(if(action in setOf("fixed_touch","chord"))token("offset").toFloat()else 0f);val limit=if(key.endsWith(".x"))h.width else h.height;require(effective>=0&&effective<limit&&actual>=0&&actual<limit){"$path.$key: outside profile plane"}
        }
        return b
    }
    fun fromLegacy(name:String,lines:Set<String>):ProfileDocument {
        StructuredProfiles.document(lines)?.let {return it.copy(profile=it.profile.copy(name=name))}
        val validated=xtr.keymapper.keymap.ProfileConfiguration.validate(lines.joinToString("\n"))
        val p=xtr.keymapper.keymap.KeymapProfiles.getProfile(validated)
        require(p.xRes>0&&p.yRes>0){"profile.screen: legacy profile needs saved dimensions"}
        return ProfileDocument(ProfileHeader(name,"",p.packageName,!p.disabled,p.xRes,p.yRes),validated.filterNot{it.startsWith("APPLICATION ")||it.startsWith("SCREENSIZE ")||it=="ENABLED"}.sorted().map{MappingFields.fromLine(it)}).validate()
    }
    fun encode(d:ProfileDocument,yaml:Boolean=false):String {
        d.validate();val h=d.profile
        val value=linkedMapOf<String,Any>("schema_version" to 1,"profile" to linkedMapOf("name" to h.name,"description" to h.description,"game_package" to h.gamePackage,"isEnabled" to h.enabled,"screen" to linkedMapOf("width" to h.width,"height" to h.height)),"bindings" to d.bindings.map{MappingFields.map(it)})
        return if(yaml) {
            val options=DumperOptions().apply{defaultFlowStyle=DumperOptions.FlowStyle.BLOCK;isPrettyFlow=true}
            val representer=object:org.yaml.snakeyaml.representer.Representer(options) {init {
                representers[java.lang.Double::class.java]=org.yaml.snakeyaml.representer.Represent { number ->
                    val plain=java.math.BigDecimal.valueOf(number as Double).toPlainString()
                    representScalar(Tag.FLOAT,if(plain.contains('.'))plain else "$plain.0")
                }
            }}
            Yaml(representer,options).dump(value)
        } else JSONObject(value).toString(2)
    }
    private fun yamlValue(text:String):Any? {
        val options=LoaderOptions().apply{isAllowDuplicateKeys=false;maxAliasesForCollections=0;nestingDepthLimit=32;codePointLimit=MAX_BYTES;tagInspector=org.yaml.snakeyaml.inspector.TagInspector{false}}
        val parser=Yaml(SafeConstructor(options))
        var tokens=0
        val scanner=org.yaml.snakeyaml.scanner.ScannerImpl(org.yaml.snakeyaml.reader.StreamReader(text),options)
        while(true){ scanner.peekToken();val token=scanner.token;require(++tokens<=30000){"file: YAML token budget exceeded"};require(token !is org.yaml.snakeyaml.tokens.AnchorToken && token !is org.yaml.snakeyaml.tokens.AliasToken && token !is org.yaml.snakeyaml.tokens.TagToken){"file: explicit YAML tags/anchors/aliases prohibited"};if(token.tokenId==org.yaml.snakeyaml.tokens.Token.ID.StreamEnd)break }
        val node=parser.compose(text.reader()) ?: error("file: empty YAML")
        fun convert(n:Node,depth:Int):Any? {
            require(depth<=32&&n.anchor==null){"file: depth/anchor limit"}
            return when(n) {
                is MappingNode->{val out=linkedMapOf<String,Any?>();n.value.forEach{pair->require(pair.keyNode is ScalarNode&&pair.keyNode.tag==Tag.STR){"file: string keys required"};val key=(pair.keyNode as ScalarNode).value;require(key.length<=128){"file: YAML key length exceeded"};require(!out.containsKey(key)){"file: duplicate key $key"};out[key]=convert(pair.valueNode,depth+1)};out}
                is SequenceNode->n.value.map{convert(it,depth+1)}
                is ScalarNode->when(n.tag){Tag.STR->n.value;Tag.BOOL->{require(n.value in setOf("true","false")){"file: booleans must be true/false"};n.value=="true"};Tag.INT,Tag.FLOAT->n.value.toDoubleOrNull()?.also{require(it.isFinite())} ?: error("file: invalid number");else->error("file: unsafe/unsupported YAML tag")}
                else->error("file: aliases/custom nodes unsupported")
            }
        }
        return convert(node,0)
    }
    @Suppress("UNCHECKED_CAST") private fun obj(value:Any?,path:String):Map<String,Any?> {require(value is Map<*,*> && value.keys.all{it is String}){"$path: expected object"};return value as Map<String,Any?>}
    private fun keys(m:Map<String,Any?>,allowed:Set<String>,path:String){require(m.keys.all{it in allowed}){"$path: unknown fields ${(m.keys-allowed).take(5)}"}}
    private fun number(v:Any?,path:String):Double {require(v is Number&&v.toDouble().isFinite()){"$path: expected finite number"};return v.toDouble()}
    private fun integer(v:Any?,path:String):Int {val n=number(v,path);require(n==n.toInt().toDouble()){"$path: expected integer"};return n.toInt()}
    private fun string(v:Any?,path:String,limit:Int):String {require(v is String&&v.isNotBlank()&&v.length<=limit&&v.none{it.isISOControl()}){"$path: invalid string (limit $limit)"};return v}
    private fun optionalString(m:Map<String,Any?>,key:String,limit:Int):String {if(!m.containsKey(key))return "";val v=m[key];require(v is String&&v.length<=limit&&v.none{it.isISOControl()&&(key!="description"||it!='\n')}){"$key: invalid metadata"};return v}
    private fun bool(m:Map<String,Any?>,key:String):Boolean {if(!m.containsKey(key))return true;val v=m[key];require(v is Boolean){"$key: expected boolean"};return v}
}

/** Strict bounded JSON parser: rejects duplicate keys, trailing commas and non-JSON syntax. */
private class StrictJson(private val text:String) {
    private var i=0
    private var nodes=0
    fun read():Any? {val v=value(0);space();require(i==text.length){"file: trailing JSON data"};return v}
    private fun space(){while(i<text.length&&text[i] in " \n\r\t")i++}
    private fun value(depth:Int):Any? {
        require(++nodes<=20000){"file: JSON node budget exceeded"};require(depth<=32){"file: nesting exceeds 32"};space();require(i<text.length){"file: incomplete JSON"}
        return when(text[i]) {
            '{'->{i++;val m=linkedMapOf<String,Any?>();space();if(take('}'))m else {while(true){space();val k=quoted();require(k.length<=128){"file: JSON key length exceeded"};require(!m.containsKey(k)){"file: duplicate key $k"};space();require(take(':'));m[k]=value(depth+1);space();if(take('}'))break;require(take(','))};m}}
            '['->{i++;val a=mutableListOf<Any?>();space();if(take(']'))a else{while(true){a+=value(depth+1);space();if(take(']'))break;require(take(','))};a}}
            '"'->quoted()
            't'->{literal("true");true};'f'->{literal("false");false};'n'->{literal("null");null}
            else->{val r=Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?").find(text,i);require(r!=null&&r.range.first==i){"file: invalid JSON value"};i=r.range.last+1;r.value.toDouble().also{require(it.isFinite()){"file: nonfinite number"}}}
        }
    }
    private fun literal(s:String){require(text.startsWith(s,i));i+=s.length}
    private fun take(c:Char):Boolean {if(i<text.length&&text[i]==c){i++;return true};return false}
    private fun quoted():String {
        require(take('"')){"file: JSON keys/strings must be quoted"};val out=StringBuilder()
        while(i<text.length){val c=text[i++];if(c=='"')return out.toString();require(c>=' '){"file: control character"};if(c!='\\')out.append(c)else{require(i<text.length);when(val e=text[i++]){'"','\\','/'->out.append(e);'b'->out.append('\b');'f'->out.append('\u000c');'n'->out.append('\n');'r'->out.append('\r');'t'->out.append('\t');'u'->{require(i+4<=text.length);out.append(text.substring(i,i+4).toInt(16).toChar());i+=4};else->error("file: invalid escape")}}}
        error("file: unterminated string")
    }
}
