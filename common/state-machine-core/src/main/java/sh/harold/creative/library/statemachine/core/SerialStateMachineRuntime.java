package sh.harold.creative.library.statemachine.core;

import sh.harold.creative.library.statemachine.DispatchResult;
import sh.harold.creative.library.statemachine.ReducerResult;
import sh.harold.creative.library.statemachine.StateChange;
import sh.harold.creative.library.statemachine.TimerCommand;
import sh.harold.creative.library.statemachine.TimerKey;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public final class SerialStateMachineRuntime<C, S extends Enum<S>, E, F> implements StateMachineRuntime<S, E, F> {

    private final Object monitor = new Object();
    private final StateMachineDefinition<C, S, E, F> definition;
    private final C context;
    private final StateMachineScheduler scheduler;
    private final Consumer<? super F> effectSink;
    private final ArrayDeque<QueuedTask<S, E, F>> queue = new ArrayDeque<>();
    private final Map<TimerKey, TimerRegistration> timers = new LinkedHashMap<>();

    private S currentState;
    private boolean draining;
    private boolean closed;

    public SerialStateMachineRuntime(
            StateMachineDefinition<C, S, E, F> definition,
            C context,
            StateMachineScheduler scheduler,
            Consumer<? super F> effectSink
    ) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.context = Objects.requireNonNull(context, "context");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.effectSink = Objects.requireNonNull(effectSink, "effectSink");
        this.currentState = definition.initialState();

        LifecycleResult<E, F> startup;
        synchronized (monitor) {
            startup = definition.state(currentState).onEnter().run(context);
            applyTimerCommandsLocked(startup.timerCommands());
        }
        forwardEffects(startup.effects());
    }

    @Override
    public S currentState() {
        synchronized (monitor) {
            return currentState;
        }
    }

    @Override
    public DispatchResult<S, E, F> dispatch(E event) {
        PendingDispatch<S, E, F> pending = new PendingDispatch<>();
        boolean shouldDrain = enqueueTask(new QueuedTask<>(event, false, pending));
        if (shouldDrain) {
            drainLoop();
        }
        return pending.await();
    }

    @Override
    public void enqueue(E event) {
        boolean shouldDrain = enqueueTask(new QueuedTask<>(event, true, null));
        if (shouldDrain) {
            drainLoop();
        }
    }

    @Override
    public void close() {
        List<TimerRegistration> handles;
        List<PendingDispatch<S, E, F>> pendingDispatches = new ArrayList<>();
        synchronized (monitor) {
            if (closed) {
                return;
            }
            closed = true;
            handles = new ArrayList<>(timers.values());
            timers.clear();
            while (!queue.isEmpty()) {
                QueuedTask<S, E, F> task = queue.removeFirst();
                if (task.pending() != null) {
                    pendingDispatches.add(task.pending());
                }
            }
        }
        handles.forEach(TimerRegistration::cancel);
        IllegalStateException exception = new IllegalStateException("Runtime is closed");
        pendingDispatches.forEach(pending -> pending.fail(exception));
    }

    private boolean enqueueTask(QueuedTask<S, E, F> task) {
        synchronized (monitor) {
            ensureOpenLocked();
            queue.addLast(task);
            if (draining) {
                return false;
            }
            draining = true;
            return true;
        }
    }

    private void drainLoop() {
        while (true) {
            QueuedTask<S, E, F> task;
            synchronized (monitor) {
                if (closed) {
                    failPendingLocked(new IllegalStateException("Runtime is closed"));
                    draining = false;
                    return;
                }
                task = queue.pollFirst();
                if (task == null) {
                    draining = false;
                    return;
                }
            }

            try {
                DispatchResult<S, E, F> result;
                synchronized (monitor) {
                    ensureOpenLocked();
                    result = processEventLocked(task.event());
                }
                if (task.pending() != null) {
                    task.pending().complete(result);
                }
                if (task.forwardEffects()) {
                    forwardEffects(result.effects());
                }
            } catch (Throwable failure) {
                if (task.pending() != null) {
                    task.pending().fail(failure);
                }
                synchronized (monitor) {
                    failPendingLocked(failure);
                    draining = false;
                }
                rethrow(failure);
            }
        }
    }

    private DispatchResult<S, E, F> processEventLocked(E event) {
        Objects.requireNonNull(event, "event");
        S previousState = currentState;
        StateMachineDefinition.StateNode<C, S, E, F> current = definition.state(previousState);

        ReducerResult<S, E, F> reducerResult = Objects.requireNonNull(current.reducer().reduce(context, event), "reducer result");
        List<F> effects = new ArrayList<>(reducerResult.effects());
        List<TimerCommand<E>> timerCommands = new ArrayList<>(reducerResult.timerCommands());
        StateChange<S> stateChange = reducerResult.stateChange();

        if (stateChange instanceof StateChange.Move<S> move) {
            accumulate(current.onExit().run(context), effects, timerCommands);
            StateMachineDefinition.StateNode<C, S, E, F> next = definition.state(move.state());
            currentState = move.state();
            accumulate(next.onEnter().run(context), effects, timerCommands);
        }

        applyTimerCommandsLocked(timerCommands);
        return new DispatchResult<>(event, previousState, currentState, stateChange, effects, timerCommands);
    }

    private void accumulate(LifecycleResult<E, F> result, List<F> effects, List<TimerCommand<E>> timerCommands) {
        LifecycleResult<E, F> lifecycleResult = Objects.requireNonNull(result, "lifecycle result");
        effects.addAll(lifecycleResult.effects());
        timerCommands.addAll(lifecycleResult.timerCommands());
    }

    private void applyTimerCommandsLocked(List<TimerCommand<E>> timerCommands) {
        for (TimerCommand<E> timerCommand : timerCommands) {
            if (timerCommand instanceof TimerCommand.Schedule<E> schedule) {
                TimerRegistration previous = timers.remove(schedule.key());
                if (previous != null) {
                    previous.cancel();
                }
                TimerRegistration registration = new TimerRegistration();
                timers.put(schedule.key(), registration);
                try {
                    registration.attach(scheduler.schedule(schedule.delay(), () -> onTimerFired(schedule.key(), registration, schedule.event())));
                } catch (RuntimeException | Error failure) {
                    timers.remove(schedule.key(), registration);
                    throw failure;
                }
            } else if (timerCommand instanceof TimerCommand.Cancel<E> cancel) {
                TimerRegistration previous = timers.remove(cancel.key());
                if (previous != null) {
                    previous.cancel();
                }
            }
        }
    }

    private void onTimerFired(TimerKey key, TimerRegistration registration, E event) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(registration, "registration");
        Objects.requireNonNull(event, "event");

        boolean shouldDrain = false;
        synchronized (monitor) {
            if (closed || timers.get(key) != registration) {
                return;
            }
            timers.remove(key);
            queue.addLast(new QueuedTask<>(event, true, null));
            if (!draining) {
                draining = true;
                shouldDrain = true;
            }
        }
        if (shouldDrain) {
            drainLoop();
        }
    }

    private void failPendingLocked(Throwable failure) {
        IllegalStateException closedException = failure instanceof IllegalStateException illegalStateException
                ? illegalStateException
                : null;
        while (!queue.isEmpty()) {
            QueuedTask<S, E, F> queued = queue.removeFirst();
            if (queued.pending() != null) {
                queued.pending().fail(closedException != null ? closedException : failure);
            }
        }
    }

    private void ensureOpenLocked() {
        if (closed) {
            throw new IllegalStateException("Runtime is closed");
        }
    }

    private void forwardEffects(List<F> effects) {
        for (F effect : effects) {
            effectSink.accept(effect);
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

    private record QueuedTask<S extends Enum<S>, E, F>(E event, boolean forwardEffects, PendingDispatch<S, E, F> pending) {

        private QueuedTask {
            Objects.requireNonNull(event, "event");
        }
    }

    private static final class PendingDispatch<S extends Enum<S>, E, F> {

        private DispatchResult<S, E, F> result;
        private Throwable failure;
        private boolean completed;

        synchronized void complete(DispatchResult<S, E, F> result) {
            if (completed) {
                return;
            }
            this.result = result;
            this.completed = true;
            notifyAll();
        }

        synchronized void fail(Throwable failure) {
            if (completed) {
                return;
            }
            this.failure = failure;
            this.completed = true;
            notifyAll();
        }

        synchronized DispatchResult<S, E, F> await() {
            while (!completed) {
                try {
                    wait();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting for dispatch", exception);
                }
            }
            if (failure != null) {
                rethrow(failure);
            }
            return result;
        }
    }

    private static final class TimerRegistration implements ScheduledTask {

        private ScheduledTask delegate;
        private boolean cancelled;

        private void attach(ScheduledTask delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
            if (cancelled) {
                delegate.cancel();
            }
        }

        @Override
        public void cancel() {
            cancelled = true;
            if (delegate != null) {
                delegate.cancel();
            }
        }
    }
}
