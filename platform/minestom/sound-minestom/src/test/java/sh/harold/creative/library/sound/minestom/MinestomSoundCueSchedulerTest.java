package sh.harold.creative.library.sound.minestom;

import net.minestom.server.timer.Scheduler;
import org.junit.jupiter.api.Test;
import sh.harold.creative.library.sound.core.ScheduledCueTask;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MinestomSoundCueSchedulerTest {

    @Test
    void schedulesOnRequestedTick() {
        Scheduler scheduler = Scheduler.newScheduler();
        MinestomSoundCueScheduler soundScheduler = new MinestomSoundCueScheduler(scheduler);
        List<String> events = new ArrayList<>();

        soundScheduler.schedule(2, () -> events.add("fire"));

        scheduler.processTick();
        assertEquals(List.of(), events);

        scheduler.processTick();
        assertEquals(List.of("fire"), events);
    }

    @Test
    void cancelPreventsFutureExecution() {
        Scheduler scheduler = Scheduler.newScheduler();
        MinestomSoundCueScheduler soundScheduler = new MinestomSoundCueScheduler(scheduler);
        List<String> events = new ArrayList<>();

        ScheduledCueTask task = soundScheduler.schedule(1, () -> events.add("fire"));
        task.cancel();
        scheduler.processTick();

        assertEquals(List.of(), events);
    }
}
