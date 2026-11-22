package sh.harold.library.movement.fabric.net;

import net.kyori.adventure.key.Key;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import sh.harold.library.movement.JumpPreset;
import sh.harold.library.movement.MoveClaim;
import sh.harold.library.movement.MoveEnd;
import sh.harold.library.movement.MoveVerdict;
import sh.harold.library.movement.SlideVariant;
import sh.harold.library.movement.core.tuning.MovesetSpec;
import sh.harold.library.net.fabric.LibraryNet;
import sh.harold.library.net.fabric.NetFeature;
import sh.harold.library.spatial.Vec3;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The {@code movement} network feature (protocol 1): {@code moveset} and {@code verdict} to the client,
 * {@code claim} and {@code end} to the server. Both the server and the client service hold the feature through
 * {@link #acquire()}, so in single player one declaration serves both sides.
 */
public final class MovementPayloads {

    public static final String FEATURE = "movement";
    public static final int PROTOCOL = 1;

    private static NetFeature feature;
    private static int holders;
    private static boolean typesRegistered;

    private MovementPayloads() {
    }

    /** Declares the feature (first holder) and registers the payload types. Release with {@link #release()}. */
    public static synchronized NetFeature acquire() {
        if (holders++ == 0) {
            typesRegistered = true;
            feature = LibraryNet.declare(FEATURE, PROTOCOL);
            feature.clientboundPlay("moveset", MovesetPayload.CODEC);
            feature.clientboundPlay("verdict", VerdictPayload.CODEC);
            feature.serverboundPlay("claim", ClaimPayload.CODEC);
            feature.serverboundPlay("end", EndPayload.CODEC);
        }
        return feature;
    }

    public static synchronized void release() {
        if (holders > 0 && --holders == 0) {
            feature.close();
            feature = null;
        }
    }

    /** Registers the payload types without leaving the feature declared; run once at mod init on both sides. */
    public static synchronized void registerTypes() {
        if (typesRegistered) {
            return;
        }
        typesRegistered = true;
        acquire();
        release();
    }

    public record MovesetPayload(MovesetSpec spec) implements CustomPacketPayload {
        public static final Type<MovesetPayload> TYPE = LibraryNet.type(FEATURE, "moveset");
        public static final StreamCodec<RegistryFriendlyByteBuf, MovesetPayload> CODEC =
                CustomPacketPayload.codec(MovesetPayload::write, MovesetPayload::read);

        private void write(FriendlyByteBuf buf) {
            buf.writeVarInt(spec.enabled().size());
            spec.enabled().forEach(key -> buf.writeUtf(key.asString()));
            buf.writeVarInt(spec.texts().size());
            spec.texts().forEach((file, text) -> {
                buf.writeUtf(file);
                buf.writeUtf(text, 1 << 18);
            });
            buf.writeUtf(spec.jumpPreset().map(Enum::name).orElse(""));
            buf.writeUtf(spec.slideVariant().map(Enum::name).orElse(""));
        }

        private static MovesetPayload read(FriendlyByteBuf buf) {
            Set<Key> enabled = new LinkedHashSet<>();
            for (int i = buf.readVarInt(); i > 0; i--) {
                enabled.add(Key.key(buf.readUtf()));
            }
            Map<String, String> texts = new LinkedHashMap<>();
            for (int i = buf.readVarInt(); i > 0; i--) {
                texts.put(buf.readUtf(), buf.readUtf(1 << 18));
            }
            String preset = buf.readUtf();
            String variant = buf.readUtf();
            return new MovesetPayload(new MovesetSpec(enabled, texts,
                    preset.isEmpty() ? Optional.empty() : Optional.of(JumpPreset.valueOf(preset)),
                    variant.isEmpty() ? Optional.empty() : Optional.of(SlideVariant.valueOf(variant))));
        }

        @Override
        public Type<MovesetPayload> type() {
            return TYPE;
        }
    }

    public record ClaimPayload(MoveClaim claim) implements CustomPacketPayload {
        public static final Type<ClaimPayload> TYPE = LibraryNet.type(FEATURE, "claim");
        public static final StreamCodec<RegistryFriendlyByteBuf, ClaimPayload> CODEC =
                CustomPacketPayload.codec(ClaimPayload::write, ClaimPayload::read);

        private void write(FriendlyByteBuf buf) {
            buf.writeVarInt(claim.seq());
            buf.writeUtf(claim.move().asString());
            buf.writeVarLong(claim.clientTick());
            writeVec(buf, claim.position());
            writeVec(buf, claim.velocity());
            writeVec(buf, claim.normal());
            buf.writeDouble(claim.ledgeTop());
            buf.writeDouble(claim.fallDistance());
            buf.writeVarInt(claim.pressTickOffset() + 64);
        }

        private static ClaimPayload read(FriendlyByteBuf buf) {
            return new ClaimPayload(new MoveClaim(buf.readVarInt(), Key.key(buf.readUtf()), buf.readVarLong(),
                    readVec(buf), readVec(buf), readVec(buf), buf.readDouble(), buf.readDouble(), buf.readVarInt() - 64));
        }

        @Override
        public Type<ClaimPayload> type() {
            return TYPE;
        }
    }

    public record EndPayload(MoveEnd end) implements CustomPacketPayload {
        public static final Type<EndPayload> TYPE = LibraryNet.type(FEATURE, "end");
        public static final StreamCodec<RegistryFriendlyByteBuf, EndPayload> CODEC =
                CustomPacketPayload.codec(EndPayload::write, EndPayload::read);

        private void write(FriendlyByteBuf buf) {
            buf.writeVarInt(end.seq());
            buf.writeVarLong(end.clientTick());
            buf.writeEnum(end.reason());
        }

        private static EndPayload read(FriendlyByteBuf buf) {
            return new EndPayload(new MoveEnd(buf.readVarInt(), buf.readVarLong(), buf.readEnum(MoveEnd.EndReason.class)));
        }

        @Override
        public Type<EndPayload> type() {
            return TYPE;
        }
    }

    public record VerdictPayload(MoveVerdict verdict) implements CustomPacketPayload {
        public static final Type<VerdictPayload> TYPE = LibraryNet.type(FEATURE, "verdict");
        public static final StreamCodec<RegistryFriendlyByteBuf, VerdictPayload> CODEC =
                CustomPacketPayload.codec(VerdictPayload::write, VerdictPayload::read);

        private void write(FriendlyByteBuf buf) {
            buf.writeVarInt(verdict.seq());
            buf.writeBoolean(verdict.accepted());
            buf.writeUtf(verdict.reason());
            buf.writeBoolean(verdict.position().isPresent());
            verdict.position().ifPresent(v -> writeVec(buf, v));
            buf.writeBoolean(verdict.velocity().isPresent());
            verdict.velocity().ifPresent(v -> writeVec(buf, v));
        }

        private static VerdictPayload read(FriendlyByteBuf buf) {
            int seq = buf.readVarInt();
            boolean accepted = buf.readBoolean();
            String reason = buf.readUtf();
            Optional<Vec3> position = buf.readBoolean() ? Optional.of(readVec(buf)) : Optional.empty();
            Optional<Vec3> velocity = buf.readBoolean() ? Optional.of(readVec(buf)) : Optional.empty();
            return new VerdictPayload(new MoveVerdict(seq, accepted, reason, position, velocity));
        }

        @Override
        public Type<VerdictPayload> type() {
            return TYPE;
        }
    }

    static void writeVec(FriendlyByteBuf buf, Vec3 v) {
        buf.writeDouble(v.x());
        buf.writeDouble(v.y());
        buf.writeDouble(v.z());
    }

    static Vec3 readVec(FriendlyByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }
}
