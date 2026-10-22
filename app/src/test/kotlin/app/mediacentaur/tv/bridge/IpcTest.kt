package app.mediacentaur.tv.bridge

import app.mediacentaur.tv.player.Mpv
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IpcTest {
    private class FakeMpv : Mpv {
        val commands = mutableListOf<List<String>>()
        val observed = mutableListOf<Pair<String, Int>>()
        val set = mutableListOf<Pair<String, String>>()
        val strings = mutableMapOf<String, String>()
        val doubles = mutableMapOf<String, Double>()
        var stops = 0
        override var listener: Mpv.Listener? = null
        override fun command(args: List<String>) { commands += args }
        override fun getString(name: String) = strings[name]
        override fun getDouble(name: String) = doubles[name]
        override fun getLong(name: String): Long? = null
        override fun getBoolean(name: String): Boolean? = null
        override fun setString(name: String, value: String) { set += name to value }
        override fun observe(name: String, format: Int) { observed += name to format }
        override fun stop() { stops++ }
    }

    private class Rig {
        val mpv = FakeMpv()
        var clock = 0L
        val scheduled = mutableListOf<Pair<Long, () -> Unit>>()
        val out = mutableListOf<JSONObject>()
        val ipc = Ipc(mpv, now = { clock }, schedule = { delay, action -> scheduled += delay to action }).also { ipc ->
            ipc.sink = { out += JSONObject(it) }
        }
        fun send(json: String) = ipc.handle(json)
        fun last() = out.last()
    }

    @Test
    fun `observe_property records the host's id and picks the property's format`() {
        val rig = Rig()
        rig.send("""{"command":["observe_property",5,"track-list"]}""")
        assertEquals("track-list" to Mpv.Format.STRING, rig.mpv.observed.single())
        assertEquals("success", rig.last().getString("error"))

        rig.mpv.listener!!.onProperty("track-list", """[{"id":1,"type":"video"}]""")
        val event = rig.last()
        assertEquals("property-change", event.getString("event"))
        assertEquals(5, event.getInt("id"))
        assertEquals("video", event.getJSONArray("data").getJSONObject(0).getString("type"))
    }

    @Test
    fun `typed properties observe in their format and pass through`() {
        val rig = Rig()
        rig.send("""{"command":["observe_property",3,"pause"]}""")
        rig.send("""{"command":["observe_property",2,"duration"]}""")
        assertEquals(Mpv.Format.FLAG, rig.mpv.observed[0].second)
        assertEquals(Mpv.Format.DOUBLE, rig.mpv.observed[1].second)
        rig.mpv.listener!!.onProperty("pause", true)
        assertTrue(rig.last().getBoolean("data"))
        rig.mpv.listener!!.onProperty("duration", 90.5)
        assertEquals(90.5, rig.last().getDouble("data"), 0.0)
    }

    @Test
    fun `get_property answers with request_id and a typed value`() {
        val rig = Rig()
        rig.mpv.doubles["time-pos"] = 12.25
        rig.send("""{"command":["get_property","time-pos"],"request_id":7}""")
        assertEquals(7, rig.last().getInt("request_id"))
        assertEquals(12.25, rig.last().getDouble("data"), 0.0)
    }

    @Test
    fun `script-opts is answered as a map, as the Unix socket renders it`() {
        val rig = Rig()
        rig.mpv.strings["script-opts"] = "media_centaur_session-profile_id=abc,osc-visibility=auto"
        rig.send("""{"command":["get_property","script-opts"],"request_id":1}""")
        assertEquals("abc", rig.last().getJSONObject("data").getString("media_centaur_session-profile_id"))
    }

    @Test
    fun `an unavailable property is an error reply`() {
        val rig = Rig()
        rig.send("""{"command":["get_property","path"],"request_id":2}""")
        assertEquals("property unavailable", rig.last().getString("error"))
        assertFalse(rig.last().has("data"))
    }

    @Test
    fun `set_property and loadfile reach mpv as strings`() {
        val rig = Rig()
        rig.send("""{"command":["set_property","keep-open","yes"]}""")
        assertEquals("keep-open" to "yes", rig.mpv.set.single())
        rig.send("""{"command":["loadfile","http://h/media/1?t=x","replace",-1,"start=12.5"]}""")
        assertEquals(listOf("loadfile", "http://h/media/1?t=x", "replace", "-1", "start=12.5"), rig.mpv.commands.single())
        rig.send("""{"command":["change-list","script-opts","append","k=v"]}""")
        assertEquals(listOf("change-list", "script-opts", "append", "k=v"), rig.mpv.commands.last())
    }

    @Test
    fun `quit stops the file and the end-file reason comes from the player`() {
        val rig = Rig()
        rig.send("""{"command":["quit"]}""")
        assertEquals(1, rig.mpv.stops)
        rig.mpv.listener!!.onEvent("end-file", "stop")
        assertEquals("end-file", rig.last().getString("event"))
        assertEquals("stop", rig.last().getString("reason"))
    }

    @Test
    fun `aid and sid render as mpv's scalars`() {
        val rig = Rig()
        rig.mpv.listener!!.onProperty("aid", "2")
        assertEquals(2, rig.last().getInt("data"))
        rig.mpv.listener!!.onProperty("sid", "no")
        assertFalse(rig.last().getBoolean("data"))
    }

    @Test
    fun `a property with no data carries none`() {
        val rig = Rig()
        rig.mpv.listener!!.onProperty("path", null)
        assertFalse(rig.last().has("data"))
        assertNull(rig.last().opt("data"))
    }

    @Test
    fun `time-pos is coalesced to one event a second carrying the latest value`() {
        val rig = Rig()
        val listener = rig.mpv.listener!!
        rig.clock = 10_000
        listener.onProperty("time-pos", 1.0)
        assertEquals(1, rig.out.size)
        rig.clock = 10_200
        listener.onProperty("time-pos", 1.2)
        rig.clock = 10_400
        listener.onProperty("time-pos", 1.4)
        assertEquals(1, rig.out.size)
        assertEquals(1, rig.scheduled.size)
        assertEquals(800L, rig.scheduled.single().first)
        rig.clock = 11_000
        rig.scheduled.single().second()
        assertEquals(2, rig.out.size)
        assertEquals(1.4, rig.last().getDouble("data"), 0.0)
    }

    @Test
    fun `malformed frames get an error reply, never a crash`() {
        val rig = Rig()
        rig.send("not json")
        assertEquals("invalid parameter", rig.last().getString("error"))
        rig.send("""{"command":[]}""")
        assertEquals("invalid parameter", rig.last().getString("error"))
    }
}
