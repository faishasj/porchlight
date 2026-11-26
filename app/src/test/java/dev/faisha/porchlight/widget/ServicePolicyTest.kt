package dev.faisha.porchlight.widget

import dev.faisha.porchlight.widget.ServicePolicy.Verdict
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ServicePolicyTest {

    private val home = HomeConfig(
        rooms = listOf(
            RoomConfig("living_room", "Living", "living", "script.vacuum_living_room"),
            RoomConfig("bedroom", "Bedroom", "bed", null),
        ),
        vacuum = "vacuum.robot",
        tv = TvConfig("media_player.tv", null),
        allOffScript = "script.all_off",
        sensors = emptyList(),
    )

    private fun entity(id: String) = JSONObject().put("entity_id", id)
    private fun area(id: String) = JSONObject().put("area_id", id)
    private fun check(domain: String, service: String, data: JSONObject, locked: Boolean = false) =
        ServicePolicy.check(home, domain, service, data, locked)

    @Test fun `the widget's own calls are allowed when unlocked`() {
        assertEquals(Verdict.Allowed, check("light", "toggle", entity("light.desk")))
        assertEquals(Verdict.Allowed, check("light", "turn_on", area("living_room")))
        assertEquals(Verdict.Allowed, check("light", "turn_off", area("bedroom")))
        assertEquals(Verdict.Allowed, check("scene", "turn_on", entity("scene.movie")))
        assertEquals(Verdict.Allowed, check("vacuum", "start", entity("vacuum.robot")))
        assertEquals(Verdict.Allowed, check("vacuum", "return_to_base", entity("vacuum.robot")))
        assertEquals(Verdict.Allowed, check("script", "turn_on", entity("script.vacuum_living_room")))
        assertEquals(Verdict.Allowed, check("media_player", "media_play_pause", entity("media_player.tv")))
    }

    @Test fun `lights and vacuum need an unlocked phone, scenes and TV don't`() {
        assertEquals(Verdict.NeedsUnlock, check("light", "toggle", entity("light.desk"), locked = true))
        assertEquals(Verdict.NeedsUnlock, check("light", "turn_off", area("bedroom"), locked = true))
        assertEquals(Verdict.NeedsUnlock, check("vacuum", "pause", entity("vacuum.robot"), locked = true))
        assertEquals(Verdict.NeedsUnlock, check("script", "turn_on", entity("script.vacuum_living_room"), locked = true))
        assertEquals(Verdict.Allowed, check("scene", "turn_on", entity("scene.movie"), locked = true))
        assertEquals(Verdict.Allowed, check("media_player", "media_play_pause", entity("media_player.tv"), locked = true))
    }

    @Test fun `anything else is refused`() {
        assertEquals(Verdict.Refused, check("lock", "unlock", entity("lock.front_door")))
        assertEquals(Verdict.Refused, check("script", "turn_on", entity("script.all_off"))) // only via AllOffAction
        assertEquals(Verdict.Refused, check("script", "turn_on", entity("script.anything")))
        assertEquals(Verdict.Refused, check("light", "turn_on", area("garage")))
        assertEquals(Verdict.Refused, check("light", "toggle", entity("switch.heater")))
        assertEquals(Verdict.Refused, check("vacuum", "start", entity("vacuum.other")))
        assertEquals(Verdict.Refused, check("media_player", "turn_off", entity("media_player.tv")))
        assertEquals(Verdict.Refused, check("light", "turn_on", entity("light.desk").put("brightness", 255)))
        assertEquals(Verdict.Refused, check("light", "turn_on", JSONObject()))
    }
}
