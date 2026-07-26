package sh.harold.library.npc.behavior.core;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.junit.jupiter.api.Test;
import sh.harold.library.npc.behavior.NpcSoundProfile;
import sh.harold.library.npc.behavior.NpcVoiceDeliveryStyle;
import sh.harold.library.npc.behavior.NpcVoiceProfile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class NpcVoiceDeliveryTest {
    private static final NpcSoundProfile SOUND = NpcSoundProfile.of(new NpcSoundProfile.Variant(
            Key.key("minecraft", "entity.villager.ambient"),
            Sound.Source.NEUTRAL,
            1.0f,
            1.0f
    ));

    @Test
    void deliveryStylesMateriallyColorTheSelectedVoiceCue() {
        NpcVoiceDelivery.Cue neutral = cue(NpcVoiceDeliveryStyle.NEUTRAL);
        NpcVoiceDelivery.Cue bright = cue(NpcVoiceDeliveryStyle.BRIGHT);
        NpcVoiceDelivery.Cue gruff = cue(NpcVoiceDeliveryStyle.GRUFF);
        NpcVoiceDelivery.Cue sleepy = cue(NpcVoiceDeliveryStyle.SLEEPY);

        assertEquals(1.0f, neutral.volume());
        assertEquals(1.0f, neutral.pitch());
        assertNotEquals(neutral.pitch(), bright.pitch());
        assertNotEquals(bright.pitch(), gruff.pitch());
        assertNotEquals(gruff.volume(), sleepy.volume());
    }

    private static NpcVoiceDelivery.Cue cue(NpcVoiceDeliveryStyle style) {
        return NpcVoiceDelivery.select(new NpcVoiceProfile(SOUND, style), new MinimumRandom()).orElseThrow();
    }

    private static final class MinimumRandom implements NpcBehaviorRandom {
        @Override
        public int nextInt(int originInclusive, int boundExclusive) {
            return originInclusive;
        }

        @Override
        public double nextDouble() {
            return 0.0;
        }
    }
}
