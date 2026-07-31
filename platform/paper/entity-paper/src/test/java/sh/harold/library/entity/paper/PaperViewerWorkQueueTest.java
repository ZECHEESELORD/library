package sh.harold.library.entity.paper;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperViewerWorkQueueTest {

    @Test
    void interactiveWorkOvertakesABackgroundBacklogAndBackgroundDrainIsBounded() {
        PaperViewerWorkQueue<String> queue = new PaperViewerWorkQueue<>();
        for (int index = 0; index < 1_000; index++) {
            queue.add("background-" + index, ViewerWorkPriority.BACKGROUND);
        }
        queue.add("interaction-bubble", ViewerWorkPriority.INTERACTIVE);
        List<String> delivered = new ArrayList<>();

        assertEquals(65, queue.drainPrioritized(64, 64, delivered::add));

        assertEquals("interaction-bubble", delivered.getFirst());
        assertEquals(65, delivered.size());
    }

    @Test
    void retirementCanRemoveOrDrainBothPriorities() {
        PaperViewerWorkQueue<String> queue = new PaperViewerWorkQueue<>();
        queue.add("background", ViewerWorkPriority.BACKGROUND);
        queue.add("interaction", ViewerWorkPriority.INTERACTIVE);

        assertTrue(queue.remove("background"));
        List<String> retired = new ArrayList<>();
        queue.drainAll(retired::add);

        assertEquals(List.of("interaction"), retired);
    }

    @Test
    void interactiveVisibilityTransitionsRetainCausalOrderAcrossBackgroundWork() {
        PaperViewerWorkQueue<String> queue = new PaperViewerWorkQueue<>();
        queue.add("reveal", ViewerWorkPriority.INTERACTIVE);
        queue.add("gaze", ViewerWorkPriority.BACKGROUND);
        queue.add("hide", ViewerWorkPriority.INTERACTIVE);
        List<String> delivered = new ArrayList<>();

        queue.drainPrioritized(64, 64, delivered::add);

        assertEquals(List.of("reveal", "hide", "gaze"), delivered);
    }
}
