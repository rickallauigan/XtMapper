package xtr.keymapper.devices

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Separate from profile preferences. One versioned snapshot keeps groups and references consistent. */
class DeviceMappingStore(context: Context) {
    private val prefs = context.getSharedPreferences("device_mapping_v1", Context.MODE_PRIVATE)
    private fun snapshot(): JSONObject = try {
        JSONObject(prefs.getString("schema_v1", null) ?: "{\"version\":1}")
    } catch (_: Exception) { JSONObject().put("version", 1) }

    fun listGroups(): List<DeviceGroup> {
        val array = snapshot().optJSONArray("groups") ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            try {
                val g = array.getJSONObject(index)
                val members = g.getJSONArray("members")
                DeviceGroup(g.getString("id"), g.getString("name"), (0 until members.length()).map {
                    val m = members.getJSONObject(it)
                    DeviceMember(m.getString("identity"), m.getString("name"), m.optInt("vendor"), m.optInt("product"))
                }, g.getLong("createdAt"))
            } catch (_: Exception) { null }
        }.sortedWith(compareBy<DeviceGroup> { it.createdAt }.thenBy { it.id })
    }
    fun listBindings(): List<ProfileBinding> {
        val array = snapshot().optJSONArray("bindings") ?: return emptyList()
        return (0 until array.length()).mapNotNull {
            try {
                val b = array.getJSONObject(it)
                ProfileBinding(b.getString("group"), b.getString("package"), b.getString("profile"))
            } catch (_: Exception) { null }
        }
    }
    private fun save(groups: List<DeviceGroup>, bindings: List<ProfileBinding>) {
        val root = JSONObject().put("version", 1)
        root.put("groups", JSONArray().apply { groups.forEach { g ->
            put(JSONObject().put("id", g.id).put("name", g.name).put("createdAt", g.createdAt)
                .put("members", JSONArray().apply { g.members.distinctBy { it.identity }.forEach { m ->
                    put(JSONObject().put("identity", m.identity).put("name", m.name)
                        .put("vendor", m.vendorId).put("product", m.productId))
                } }))
        } })
        root.put("bindings", JSONArray().apply { bindings.forEach {
            put(JSONObject().put("group", it.groupId).put("package", it.packageName).put("profile", it.profileName))
        } })
        prefs.edit().putString("schema_v1", root.toString()).apply()
    }
    fun createGroup(name: String, members: List<DeviceMember>): DeviceGroup {
        val group = DeviceGroup(UUID.randomUUID().toString(), name.trim(), members.distinctBy { it.identity }, System.currentTimeMillis())
        save(listGroups() + group, listBindings())
        return group
    }
    fun updateGroup(group: DeviceGroup) = save(listGroups().map { if (it.id == group.id) group else it }, listBindings())
    fun renameGroup(id: String, name: String) { listGroups().find { it.id == id }?.let { updateGroup(it.copy(name = name.trim())) } }
    fun updateMembers(id: String, members: List<DeviceMember>) { listGroups().find { it.id == id }?.let { updateGroup(it.copy(members = members)) } }
    fun deleteGroup(id: String) = save(listGroups().filterNot { it.id == id }, listBindings().filterNot { it.groupId == id })
    fun setBinding(groupId: String, packageName: String, profileName: String?) {
        if (listGroups().none { it.id == groupId }) return
        val remaining = listBindings().filterNot { it.groupId == groupId && it.packageName == packageName }
        save(listGroups(), if (profileName == null) remaining else remaining + ProfileBinding(groupId, packageName, profileName))
    }
}
