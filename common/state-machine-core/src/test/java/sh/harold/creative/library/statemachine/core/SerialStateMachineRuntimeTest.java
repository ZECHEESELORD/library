package sh.harold.creative.library.statemachine.core;

import org.junit.jupiter.api.Test;
import sh.harold.creative.library.statemachine.DispatchResult;
import sh.harold.creative.library.statemachine.ReducerResult;
import sh.harold.creative.library.statemachine.StateChange;
import sh.harold.creative.library.statemachine.TimerCommand;
import sh.harold.creative.library.statemachine.TimerKey;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SerialStateMachineRuntimeTest {

    private static final TimerKey TIMEOUT = new TimerKey("timeout");

    @Test
    void directDispatchReturnsReportAndDoesNotUseEffectSink() {
        TestContext context = new TestContext();
        List<String> sink = new ArrayList<>();
        StateMachineRuntime<TestState, TestEvent, TestEffect> runtime = baseDefinition().createRuntime(context, StateMachineScheduler.unsupported(), effect -> sink.add(effect.value()));

        DispatchResult<TestState, TestEvent, TestEffect> result = runtime.dispatch(new PingEvent("hello"));

        assertEquals(TestState.IDLE, result.previousState());
        assertEquals(TestState.IDLE, result.currentState());
        assertTrue(result.stateChange().isStay());
        assertEquals(List.of(new TestEffect("pong:hello")), result.effects());
        assertEquals(List.of("ping:hello"), context.log);
        assertTrue(sink.isEmpty());
    }

    @Test
    void moveRunsExitThenEnterInOrder() {
        TestContext context = new TestContext();
        List<String> sink = new ArrayList<>();
        StateMachineRuntime<TestState, TestEvent, TestEffect> runtime = transitionDefinition().createRuntime(context, StateMachineScheduler.unsupported(), effect -> sink.add(effect.value()));

        DispatchResult<TestState, TestEvent, TestEffect> result = runtime.dispatch(new ActivateEvent());

        assertEquals(TestState.IDLE, result.previousState());
        assertEquals(TestState.ACTIVE, result.currentState());
        assertEquals(
                List.of(new TestEffect("reducer"), new TestEffect("exit-idle"), new TestEffect("enter-active")),
                result.effects()
        );
        assertEquals(List.of("reduce:activate", "exit:idle", "enter:active"), context.log);
        assertTrue(sink.isEmpty());
    }

    @Test
    void moveToSameStateReentersInsteadOfStaying() {
        TestContext context = new TestContext();
        List<String> sink = new ArrayList<>();
        StateMachineRuntime<TestState, TestEvent, TestEffect> runtime = reentryDefinition().createRuntime(context, StateMachineScheduler.unsupported(), effect -> sink.add(effect.value()));
        sink.clear();
        context.log.clear();

        DispatchResult<TestState, TestEvent, TestEffect> result = runtime.dispatch(new ReenterEvent());

        StateChange.Move<TestState> change = assertInstanceOf(StateChange.Move.class, result.stateChange());
        assertEquals(TestState.IDLE, change.state());
        assertEquals(List.of(new TestEffect("reducer"), new TestEffect("exit-idle"), new TestEffect("enter-idle")), result.effects());
        assertEquals(List.of("reduce:reenter", "exit:idle", "enter:idle"), context.log);
        assertTrue(sink.isEmpty());
    }

    @Test
    void schedulingSameTimerKeyReplacesPreviousHandle() {
        ManualScheduler scheduler = new ManualScheduler();
        TestContext context = new TestContext();
        StateMachineRuntime<TestState, TestEvent, TestEffect> runtime = timerDefinition().createRuntime(context, scheduler, effect -> {
        });

        runtime.dispatch(new StartTimerEvent("first"));
        runtime.dispatch(new StartTimerEvent("second"));

        assertEquals(2, scheduler.tasks.size());
        assertTrue(scheduler.tasks.get(0).cancelled);
        assertTrue(!scheduler.tasks.get(1).cancelled);
    }

    @Test
    void timerFireEnqueuesConfiguredEventThroughSink() {
        ManualScheduler scheduler = new ManualScheduler();
        TestContext context = new TestContext();
        List<String> sink = new ArrayList<>();
        StateMachineRuntime<TestState, TestEvent, TestEffect> runtime = timerDefinition().createRuntime(context, scheduler, effect -> sink.add(effect.value()));

        runtime.dispatch(new StartTimerEvent("later"));
        scheduler.tasks.get(0).fire();

        assertEquals(List.of("timer:later"), sink);
        assertEquals(List.of("reduce:start-timer:later", "reduce:timer:later"), context.log);
    }

    @Test
    void cancelAndCloseCancelOutstandingTimersAndRejectMoreWork() {
        ManualScheduler scheduler = new ManualScheduler();
        TestContext context = new TestContext();
        StateMachineRuntime<TestState, TestEvent, TestEffect> runtime = timerDefinition().createRuntime(context, scheduler, effect -> {
        });

        runtime.dispatch(new StartTimerEvent("first"));
        runtime.dispatch(new CancelTimerEvent());
        runtime.dispatch(new StartTimerEvent("second"));

        assertTrue(scheduler.tasks.get(0).cancelled);
        assertTrue(!scheduler.tasks.get(1).cancelled);

        runtime.close();

        assertTrue(scheduler.tasks.get(1).cancelled);
        assertThrows(IllegalStateException.class, () -> runtime.dispatch(new PingEvent("blocked")));
        assertThrows(IllegalStateException.class, () -> runtime.enqueue(new PingEvent("blocked")));
    }

    @Test
    void startupEnterHookUsesEffectSinkAndCanScheduleTimers() {
        ManualScheduler scheduler = new ManualScheduler();
        TestContext context = new TestContext();
        List<String> sink = new ArrayList<>();
        StateMachineRuntime<TestState, TestEvent, TestEffect> runtime = startupDefinition().createRuntime(context, scheduler, effect -> sink.add(effect.value()));

        assertEquals(TestState.IDLE, runtime.currentState());
        assertEquals(List.of("startup"), sink);
        assertEquals(List.of("enter:startup"), context.log);

        scheduler.tasks.get(0).fire();

        assertEquals(List.of("startup", "timer:startup"), sink);
        assertEquals(List.of("enter:startup", "reduce:timer:startup"), context.log);
    }

    @Test
    void enqueueStaysSerialWhenEffectSinkQueuesFollowUpWork() {
        TestContext context = new TestContext();
        AtomicReference<SerialStateMachineRuntime<TestContext, TestState, TestEvent, TestEffect>> holder = new AtomicReference<>();
        List<String> sink = new ArrayList<>();
        SerialStateMachineRuntime<TestContext, TestState, TestEvent, TestEffect> runtime = new SerialStateMachineRuntime<>(
                enqueueDefinition(),
                context,
                StateMachineScheduler.unsupported(),
                effect -> {
                    sink.add(effect.value());
                    context.log.add("sink:" + effect.value());
                    if (effect.value().equals("queue:follow-up")) {
                        holder.get().enqueue(new FollowUpEvent());
                    }
                }
        );
        holder.set(runtime);

        runtime.enqueue(new QueueEffectEvent());

        assertEquals(
                List.of("reduce:queue", "sink:queue:follow-up", "reduce:follow-up", "sink:follow-up"),
                context.log
        );
        assertEquals(List.of("queue:follow-up", "follow-up"), sink);
    }

    @Test
    void exceptionsAbortDispatchWithoutChangingState() {
        TestContext context = new TestContext();
        StateMachineRuntime<TestState, TestEvent, TestEffect> runtime = failureDefinition().createRuntime(context, StateMachineScheduler.unsupported(), effect -> {
        });

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> runtime.dispatch(new FailEvent()));

        assertEquals("boom", exception.getMessage());
        assertEquals(TestState.IDLE, runtime.currentState());
        assertEquals(List.of("reduce:fail"), context.log);
    }

    private static StateMachineDefinition<TestContext, TestState, TestEvent, TestEffect> baseDefinition() {
        return StateMachineDefinition.<TestContext, TestState, TestEvent, TestEffect>builder(TestState.IDLE)
                .state(TestState.IDLE, state -> state.reducer((context, event) -> switch (event) {
                    case PingEvent ping -> {
                        context.log.add("ping:" + ping.message());
                        yield ReducerResult.stay(List.of(new TestEffect("pong:" + ping.message())), List.of());
                    }
                    default -> ReducerResult.stay();
                }))
                .build();
    }

    private static StateMachineDefinition<TestContext, TestState, TestEvent, TestEffect> transitionDefinition() {
        return StateMachineDefinition.<TestContext, TestState, TestEvent, TestEffect>builder(TestState.IDLE)
                .state(TestState.IDLE, state -> state
                        .reducer((context, event) -> switch (event) {
                            case ActivateEvent ignored -> {
                                context.log.add("reduce:activate");
                                yield ReducerResult.move(TestState.ACTIVE, List.of(new TestEffect("reducer")), List.of());
                            }
                            default -> ReducerResult.stay();
                        })
                        .onExit(context -> {
                            context.log.add("exit:idle");
                            return LifecycleResult.of(List.of(new TestEffect("exit-idle")), List.of());
                        }))
                .state(TestState.ACTIVE, state -> state
                        .reducer((context, event) -> ReducerResult.stay())
                        .onEnter(context -> {
                            context.log.add("enter:active");
                            return LifecycleResult.of(List.of(new TestEffect("enter-active")), List.of());
                        }))
                .build();
    }

    private static StateMachineDefinition<TestContext, TestState, TestEvent, TestEffect> reentryDefinition() {
        return StateMachineDefinition.<TestContext, TestState, TestEvent, TestEffect>builder(TestState.IDLE)
                .state(TestState.IDLE, state -> state
                        .reducer((context, event) -> switch (event) {
                            case ReenterEvent ignored -> {
                                context.log.add("reduce:reenter");
                                yield ReducerResult.move(TestState.IDLE, List.of(new TestEffect("reducer")), List.of());
                            }
                            default -> ReducerResult.stay();
                        })
                        .onEnter(context -> {
                            context.log.add("enter:idle");
                            return LifecycleResult.of(List.of(new TestEffect("enter-idle")), List.of());
                        })
                        .onExit(context -> {
                            context.log.add("exit:idle");
                            return LifecycleResult.of(List.of(new TestEffect("exit-idle")), List.of());
                        }))
                .build();
    }

    private static StateMachineDefinition<TestContext, TestState, TestEvent, TestEffect> timerDefinition() {
        return StateMachineDefinition.<TestContext, TestState, TestEvent, TestEffect>builder(TestState.IDLE)
                .state(TestState.IDLE, state -> state.reducer((context, event) -> switch (event) {
                    case StartTimerEvent start -> {
                        context.log.add("reduce:start-timer:" + start.label());
                        yield ReducerResult.stay(
                                List.of(),
                                List.of(TimerCommand.schedule(TIMEOUT, Duration.ofSeconds(5), new TimerFiredEvent(start.label())))
                        );
                    }
                    case CancelTimerEvent ignored -> ReducerResult.stay(List.of(), List.of(TimerCommand.cancel(TIMEOUT)));
                    case TimerFiredEvent fired -> {
                        context.log.add("reduce:timer:" + fired.label());
                        yield ReducerResult.stay(List.of(new TestEffect("timer:" + fired.label())), List.of());
                    }
                    default -> ReducerResult.stay();
                }))
                .build();
    }

    private static StateMachineDefinition<TestContext, TestState, TestEvent, TestEffect> startupDefinition() {
        return StateMachineDefinition.<TestContext, TestState, TestEvent, TestEffect>builder(TestState.IDLE)
                .state(TestState.IDLE, state -> state
                        .reducer((context, event) -> switch (event) {
                            case TimerFiredEvent fired -> {
                                context.log.add("reduce:timer:" + fired.label());
                                yield ReducerResult.stay(List.of(new TestEffect("timer:" + fired.label())), List.of());
                            }
                            default -> ReducerResult.stay();
                        })
                        .onEnter(context -> {
                            context.log.add("enter:startup");
                            return LifecycleResult.of(
                                    List.of(new TestEffect("startup")),
                                    List.of(TimerCommand.schedule(TIMEOUT, Duration.ofSeconds(1), new TimerFiredEvent("startup")))
                            );
                        }))
                .build();
    }

    private static StateMachineDefinition<TestContext, TestState, TestEvent, TestEffect> enqueueDefinition() {
        return StateMachineDefinition.<TestContext, TestState, TestEvent, TestEffect>builder(TestState.IDLE)
                .state(TestState.IDLE, state -> state.reducer((context, event) -> switch (event) {
                    case QueueEffectEvent ignored -> {
                        context.log.add("reduce:queue");
                        yield ReducerResult.stay(List.of(new TestEffect("queue:follow-up")), List.of());
                    }
                    case FollowUpEvent ignored -> {
                        context.log.add("reduce:follow-up");
                        yield ReducerResult.stay(List.of(new TestEffect("follow-up")), List.of());
                    }
                    default -> ReducerResult.stay();
                }))
                .build();
    }

    private static StateMachineDefinition<TestContext, TestState, TestEvent, TestEffect> failureDefinition() {
        return StateMachineDefinition.<TestContext, TestState, TestEvent, TestEffect>builder(TestState.IDLE)
                .state(TestState.IDLE, state -> state.reducer((context, event) -> switch (event) {
                    case FailEvent ignored -> {
                        context.log.add("reduce:fail");
                        throw new IllegalStateException("boom");
                    }
                    default -> ReducerResult.stay();
                }))
                .build();
    }

    private enum TestState {
        IDLE,
        ACTIVE
    }

    private sealed interface TestEvent permits PingEvent, ActivateEvent, ReenterEvent, StartTimerEvent, CancelTimerEvent, TimerFiredEvent, QueueEffectEvent, FollowUpEvent, FailEvent {
    }

    private record PingEvent(String message) implements TestEvent {
    }

    private record ActivateEvent() implements TestEvent {
    }

    private record ReenterEvent() implements TestEvent {
    }

    private record StartTimerEvent(String label) implements TestEvent {
    }

    private record CancelTimerEvent() implements TestEvent {
    }

    private record TimerFiredEvent(String label) implements TestEvent {
    }

    private record QueueEffectEvent() implements TestEvent {
    }

    private record FollowUpEvent() implements TestEvent {
    }

    private record FailEvent() implements TestEvent {
    }

    private record TestEffect(String value) {
    }

    private static final class TestContext {
        private final List<String> log = new ArrayList<>();
    }

    private static final class ManualScheduler implements StateMachineScheduler {

        private final List<ManualTask> tasks = new ArrayList<>();

        @Override
        public ScheduledTask schedule(Duration delay, Runnable action) {
            ManualTask task = new ManualTask(action);
            tasks.add(task);
            return task;
        }
    }

    private static final class ManualTask implements ScheduledTask {

        private final Runnable action;
        private boolean cancelled;
        private boolean fired;

        private ManualTask(Runnable action) {
            this.action = action;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }

        private void fire() {
            if (cancelled || fired) {
                return;
            }
            fired = true;
            action.run();
        }
    }
}
