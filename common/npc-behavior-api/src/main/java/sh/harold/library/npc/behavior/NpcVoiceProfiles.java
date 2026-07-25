package sh.harold.library.npc.behavior;

public final class NpcVoiceProfiles {
    public static final NpcVoiceProfile SILENT = new NpcVoiceProfile(
            NpcSoundProfiles.SILENT,
            NpcVoiceDeliveryStyle.NEUTRAL
    );
    public static final NpcVoiceProfile WARM_VILLAGER = new NpcVoiceProfile(
            NpcSoundProfiles.VILLAGER,
            NpcVoiceDeliveryStyle.BRIGHT
    );
    public static final NpcVoiceProfile DEEP_VILLAGER = new NpcVoiceProfile(
            NpcSoundProfiles.VILLAGER,
            NpcVoiceDeliveryStyle.GRUFF
    );
    public static final NpcVoiceProfile FROG = new NpcVoiceProfile(
            NpcSoundProfiles.FROG,
            NpcVoiceDeliveryStyle.SOFT
    );
    public static final NpcVoiceProfile HARSH_ILLAGER = new NpcVoiceProfile(
            NpcSoundProfiles.ILLAGER,
            NpcVoiceDeliveryStyle.GRUFF
    );

    private NpcVoiceProfiles() {
    }
}
