package sh.harold.creative.library.sound.core;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.sound.Sound;
import sh.harold.creative.library.sound.CuePlayback;
import sh.harold.creative.library.sound.CueStep;
import sh.harold.creative.library.sound.SoundCue;
import sh.harold.creative.library.sound.SoundCueRegistry;
import sh.harold.creative.library.sound.SoundCueService;
import sh.harold.creative.library.sound.SoundTarget;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

public final class StandardSoundCueService implements SoundCueService {

    private final Object monitor = new Object();
    private final SoundCueRegistry registry;
    private final SoundCueScheduler scheduler;
    private final Random random;
    private final Set<ActiveCuePlayback> activePlaybacks = new LinkedHashSet<>();

    private boolean closed;

    public StandardSoundCueService(SoundCueScheduler scheduler) {
        this(new StandardSoundCueRegistry(), scheduler, new Random());
    }

    public StandardSoundCueService(SoundCueScheduler scheduler, Random random) {
        this(new StandardSoundCueRegistry(), scheduler, random);
    }

    public StandardSoundCueService(SoundCueRegistry registry, SoundCueScheduler scheduler, Random random) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.random = Objects.requireNonNull(random, "random");
        this.registry.register(StockSoundCuePack.create());
    }

    @Override
    public SoundCueRegistry registry() {
        return registry;
    }

    @Override
    public CuePlayback play(SoundTarget target, SoundCue cue) {
        SoundTarget playbackTarget = Objects.requireNonNull(target, "target");
        SoundCue soundCue = Objects.requireNonNull(cue, "cue");
        ensureOpen();

        Map<Long, List<Sound>> soundsByTick = compile(soundCue);
        if (soundsByTick.isEmpty()) {
            return CuePlayback.noop();
        }

        List<Sound> immediate = soundsByTick.getOrDefault(0L, List.of());
        List<Map.Entry<Long, List<Sound>>> delayed = new ArrayList<>();
        for (Map.Entry<Long, List<Sound>> entry : soundsByTick.entrySet()) {
            if (entry.getKey() > 0) {
                delayed.add(entry);
            }
        }

        if (delayed.isEmpty()) {
            playSounds(target, immediate);
            return CuePlayback.noop();
        }

        ActiveCuePlayback playback = new ActiveCuePlayback();
        synchronized (monitor) {
            ensureOpenLocked();
            activePlaybacks.add(playback);
        }

        try {
            for (Map.Entry<Long, List<Sound>> entry : delayed) {
                List<Sound> sounds = entry.getValue();
                ScheduledCueTask task = scheduler.schedule(entry.getKey(), () -> playback.fire(playbackTarget, sounds));
                playback.addTask(task);
            }
            playSounds(playbackTarget, immediate);
            return playback;
        } catch (Throwable failure) {
            playback.cancel();
            rethrow(failure);
            return CuePlayback.noop();
        }
    }

    @Override
    public void close() {
        List<ActiveCuePlayback> playbacks;
        synchronized (monitor) {
            if (closed) {
                return;
            }
            closed = true;
            playbacks = new ArrayList<>(activePlaybacks);
            activePlaybacks.clear();
        }
        playbacks.forEach(ActiveCuePlayback::cancel);
    }

    private void ensureOpen() {
        synchronized (monitor) {
            ensureOpenLocked();
        }
    }

    private void ensureOpenLocked() {
        if (closed) {
            throw new IllegalStateException("Sound cue service is closed");
        }
    }

    private Map<Long, List<Sound>> compile(SoundCue cue) {
        TreeMap<Long, List<Sound>> grouped = new TreeMap<>();
        collect(cue, 0L, grouped);

        Map<Long, List<Sound>> compiled = new LinkedHashMap<>();
        for (Map.Entry<Long, List<Sound>> entry : grouped.entrySet()) {
            compiled.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return compiled;
    }

    private void collect(SoundCue cue, long offset, Map<Long, List<Sound>> grouped) {
        switch (cue) {
            case SoundCue.SoundEffect effect -> grouped.computeIfAbsent(offset, ignored -> new ArrayList<>()).add(effect.sound());
            case SoundCue.Sequence sequence -> {
                for (CueStep step : sequence.steps()) {
                    collect(step.cue(), Math.addExact(offset, step.tick()), grouped);
                }
            }
            case SoundCue.Layer layer -> {
                for (SoundCue child : layer.cues()) {
                    collect(child, offset, grouped);
                }
            }
            case SoundCue.Variant variant -> collect(variant.variants().get(random.nextInt(variant.variants().size())), offset, grouped);
            case SoundCue.Silent ignored -> {
            }
        }
    }

    private void playSounds(SoundTarget target, List<Sound> sounds) {
        for (Sound sound : sounds) {
            target.play(sound);
        }
    }

    private void unregister(ActiveCuePlayback playback) {
        synchronized (monitor) {
            activePlaybacks.remove(playback);
        }
    }

    private static void rethrow(Throwable failure) {
        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        throw new RuntimeException(failure);
    }

    private final class ActiveCuePlayback implements CuePlayback {

        private final Object playbackMonitor = new Object();
        private final List<ScheduledCueTask> scheduledTasks = new ArrayList<>();

        private boolean finished;
        private int remainingTasks;

        void addTask(ScheduledCueTask task) {
            synchronized (playbackMonitor) {
                if (finished) {
                    task.cancel();
                    return;
                }
                scheduledTasks.add(task);
                remainingTasks++;
            }
        }

        void fire(SoundTarget target, List<Sound> sounds) {
            synchronized (playbackMonitor) {
                if (finished) {
                    return;
                }
            }
            boolean shouldUnregister = false;
            try {
                playSounds(target, sounds);
            } finally {
                shouldUnregister = completeTask();
            }
            if (shouldUnregister) {
                unregister(this);
            }
        }

        @Override
        public void cancel() {
            List<ScheduledCueTask> tasks;
            synchronized (playbackMonitor) {
                if (finished) {
                    return;
                }
                finished = true;
                tasks = List.copyOf(scheduledTasks);
                scheduledTasks.clear();
                remainingTasks = 0;
            }
            tasks.forEach(ScheduledCueTask::cancel);
            unregister(this);
        }

        private boolean completeTask() {
            synchronized (playbackMonitor) {
                if (finished) {
                    return false;
                }
                remainingTasks--;
                if (remainingTasks == 0) {
                    finished = true;
                    scheduledTasks.clear();
                    return true;
                }
                return false;
            }
        }
    }
}
