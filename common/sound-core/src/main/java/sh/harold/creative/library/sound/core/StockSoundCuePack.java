package sh.harold.creative.library.sound.core;

import sh.harold.creative.library.sound.SoundCueKeys;
import sh.harold.creative.library.sound.SoundCuePack;
import sh.harold.creative.library.sound.SoundCuePacks;

import static sh.harold.creative.library.sound.SoundCues.atTick;
import static sh.harold.creative.library.sound.SoundCues.layer;
import static sh.harold.creative.library.sound.SoundCues.oneOf;
import static sh.harold.creative.library.sound.SoundCues.sequence;
import static sh.harold.creative.library.sound.SoundCues.sound;

final class StockSoundCuePack {

    private StockSoundCuePack() {
    }

    static SoundCuePack create() {
        return SoundCuePacks.pack(SoundCueKeys.NAMESPACE)
                .cue(SoundCueKeys.MENU_CLICK, sound("minecraft:ui.button.click", 0.7f, 1.0f))
                .cue(SoundCueKeys.INTERACTION_NPC, oneOf(
                        sound("minecraft:entity.villager.ambient", 0.55f, 0.95f),
                        sound("minecraft:entity.villager.ambient", 0.55f, 1.1f)
                ))
                .cue(SoundCueKeys.RESULT_CONFIRM, sequence(
                        atTick(0, sound("minecraft:block.note_block.pling", 0.8f, 1.05f)),
                        atTick(2, sound("minecraft:entity.experience_orb.pickup", 0.6f, 1.3f))
                ))
                .cue(SoundCueKeys.RESULT_DENY, sequence(
                        atTick(0, sound("minecraft:block.note_block.bass", 0.75f, 0.8f)),
                        atTick(2, sound("minecraft:entity.villager.no", 0.45f, 1.0f))
                ))
                .cue(SoundCueKeys.REWARD_LEVEL_UP, sequence(
                        atTick(0, sound("minecraft:block.note_block.pling", 0.85f, 1.0f)),
                        atTick(2, sound("minecraft:block.note_block.pling", 0.8f, 1.25f)),
                        atTick(4, sound("minecraft:entity.experience_orb.pickup", 0.65f, 1.55f))
                ))
                .cue(SoundCueKeys.REWARD_DISCOVERY, sequence(
                        atTick(0, layer(
                                sound("minecraft:block.amethyst_block.chime", 0.7f, 0.95f),
                                sound("minecraft:block.note_block.pling", 0.55f, 1.35f)
                        )),
                        atTick(3, sound("minecraft:entity.experience_orb.pickup", 0.55f, 1.45f))
                ))
                .build();
    }
}
