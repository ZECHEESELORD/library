package sh.harold.library.net.protocol;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetProtocolTest {

    @Test
    void matchingManifestsAreAcceptedAndExtraClientFeaturesAreFine() {
        ProtocolManifest server = new ProtocolManifest("v13", Map.of("movement", 1, "camera", 2));
        ProtocolManifest client = new ProtocolManifest("v13", Map.of("movement", 1, "camera", 2, "music", 1));
        HandshakeCheck.Verdict verdict = HandshakeCheck.compare(server, client);
        assertTrue(verdict.accepted());
        assertEquals("", verdict.message());
    }

    @Test
    void mismatchNamesEachFeature() {
        ProtocolManifest server = new ProtocolManifest("v13", Map.of("movement", 2, "camera", 1, "sequence", 1));
        ProtocolManifest client = new ProtocolManifest("v13", Map.of("movement", 1, "camera", 1));
        HandshakeCheck.Verdict verdict = HandshakeCheck.compare(server, client);
        assertFalse(verdict.accepted());
        assertEquals(List.of("movement", "sequence"), verdict.features());
        assertTrue(verdict.message().contains("feature 'movement' is protocol 2 on the server but 1 on this client"));
        assertTrue(verdict.message().contains("feature 'sequence' is missing on this client"));
    }

    @Test
    void libraryVersionMismatchIsRejected() {
        ProtocolManifest server = new ProtocolManifest("v13", Map.of("movement", 1));
        ProtocolManifest client = new ProtocolManifest("v12", Map.of("movement", 1));
        HandshakeCheck.Verdict verdict = HandshakeCheck.compare(server, client);
        assertFalse(verdict.accepted());
        assertTrue(verdict.features().isEmpty());
        assertTrue(verdict.message().contains("library v13 on the server but v12 on this client"));
        assertTrue(HandshakeCheck.missingClientMessage(server).contains("movement"));
    }

    @Test
    void payloadIdsFollowTheNamingRule() {
        assertEquals("creative_library:net/tick", PayloadIds.id("net", "tick"));
        assertEquals("movement/claim/v2", PayloadIds.path("movement", "claim/v2"));
        assertThrows(IllegalArgumentException.class, () -> PayloadIds.id("Movement", "claim"));
        assertThrows(IllegalArgumentException.class, () -> PayloadIds.id("movement", "claim/"));
        assertThrows(IllegalArgumentException.class, () -> new ProtocolManifest("v1", Map.of("bad id", 1)));
    }

    @Test
    void offsetIsTheMedianOfTheLastFiveSamples() {
        TickOffsetFilter filter = new TickOffsetFilter();
        assertTrue(filter.offset().isEmpty());
        filter.addSample(1000, 10);
        assertEquals(990, filter.offset().getAsLong());
        filter.addSample(1020, 30);
        assertEquals(990, filter.offset().getAsLong(), "lower median of two");
        filter.addSample(1040, 48);
        filter.addSample(1060, 70);
        filter.addSample(1080, 75); // a late-arriving packet makes one sample an outlier
        assertEquals(990, filter.offset().getAsLong());
        filter.addSample(1100, 110);
        filter.addSample(1120, 130);
        assertEquals(5, filter.sampleCount());
        assertEquals(990, filter.offset().getAsLong());
        assertEquals(1190, filter.toServerTick(200).getAsLong());
        filter.reset();
        assertTrue(filter.toServerTick(5).isEmpty());
    }
}
