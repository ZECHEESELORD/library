package sh.harold.library.tween.track;

import sh.harold.library.tween.EasingFunction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * An immutable keyed curve sampled at fractional clock time (catalog 1.1). Keys may be Hermite (explicit,
 * auto, flat or linear tangents), eased, stepped or two-sided cuts. Before the first key the track holds the
 * first key's left value; from the last key on it holds the last key's right value.
 *
 * <p>Sampling is a pure function of {@code t}, so the same {@code t} gives the same value at any frame rate and
 * on any seek. At a cut key, {@code t < cut} approaches the left value and {@code t >= cut} is exactly the right
 * value; no frame blends the two sides.
 */
public final class Track<T> implements StatelessSampler<T> {

    private final TrackValue<T> values;
    private final List<TrackKey<T>> keys;
    private final double[] times;
    private final List<T> inTangents;
    private final List<T> outTangents;
    private final List<Marker> markers;
    private final List<Double> cutTimes;
    private final int holdTicks;

    private Track(TrackValue<T> values, List<TrackKey<T>> keys, List<Marker> markers, int holdTicks) {
        this.values = values;
        this.keys = List.copyOf(keys);
        this.times = new double[keys.size()];
        for (int i = 0; i < keys.size(); i++) {
            times[i] = keys.get(i).time();
        }
        this.markers = List.copyOf(markers);
        this.holdTicks = holdTicks;
        List<T> in = new ArrayList<>(keys.size());
        List<T> out = new ArrayList<>(keys.size());
        List<Double> cuts = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++) {
            in.add(resolveIn(i));
            out.add(resolveOut(i));
            if (keys.get(i).cut()) {
                cuts.add(keys.get(i).time());
            }
        }
        this.inTangents = Collections.unmodifiableList(in);
        this.outTangents = Collections.unmodifiableList(out);
        this.cutTimes = List.copyOf(cuts);
    }

    public static <T> Builder<T> builder(TrackValue<T> values) {
        return new Builder<>(values);
    }

    /** Shortcut for a scalar track. */
    public static Builder<Double> scalar() {
        return new Builder<>(TrackValues.SCALAR);
    }

    public TrackValue<T> values() {
        return values;
    }

    public List<TrackKey<T>> keys() {
        return keys;
    }

    public List<Marker> markers() {
        return markers;
    }

    /** Times of every cut key, ascending. Stateful consumers reset when clock time reaches one (catalog R4). */
    public List<Double> cutTimes() {
        return cutTimes;
    }

    /** Quantise step in ticks ({@code 0} means none); 2 animates "on twos". */
    public int holdTicks() {
        return holdTicks;
    }

    public double startTime() {
        return times[0];
    }

    public double endTime() {
        return times[times.length - 1];
    }

    @Override
    public T sample(double t) {
        if (!Double.isFinite(t)) {
            throw new IllegalArgumentException("t must be finite");
        }
        double time = holdTicks > 0 ? Math.floor(t / holdTicks) * holdTicks : t;
        if (time < times[0]) {
            return keys.getFirst().left();
        }
        int index = segmentIndex(time);
        TrackKey<T> start = keys.get(index);
        if (index == keys.size() - 1 || time == start.time()) {
            return start.right();
        }
        TrackKey<T> end = keys.get(index + 1);
        double span = end.time() - start.time();
        double u = (time - start.time()) / span;
        return switch (start.out()) {
            case Interpolation.Stepped stepped -> start.right();
            case Interpolation.Eased eased -> values.lerp(start.right(), end.left(), eased.easing().apply(u));
            case Interpolation.Hermite hermite -> values.hermite(
                    start.right(), outTangents.get(index), end.left(), inTangents.get(index + 1), span, u);
        };
    }

    /** Cut times {@code c} with {@code from < c <= to}: the cuts a forward step from {@code from} to {@code to} reaches. */
    public List<Double> cutsCrossed(double from, double to) {
        List<Double> crossed = new ArrayList<>();
        for (double cut : cutTimes) {
            if (cut > from && cut <= to) {
                crossed.add(cut);
            }
        }
        return crossed;
    }

    /**
     * Markers that fire on a forward playback step from {@code from} (exclusive) to {@code to} (inclusive).
     * Backward steps fire nothing.
     */
    public List<Marker> markersCrossed(double from, double to) {
        List<Marker> crossed = new ArrayList<>();
        if (to <= from) {
            return crossed;
        }
        for (Marker marker : markers) {
            if (marker.tick() > from && marker.tick() <= to) {
                crossed.add(marker);
            }
        }
        return crossed;
    }

    /** Markers that fire on a seek to {@code t}: only sticky markers at or before {@code t}, in tick order. */
    public List<Marker> markersOnSeek(double t) {
        List<Marker> fired = new ArrayList<>();
        for (Marker marker : markers) {
            if (marker.sticky() && marker.tick() <= t) {
                fired.add(marker);
            }
        }
        return fired;
    }

    private int segmentIndex(double time) {
        int lo = 0;
        int hi = times.length - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (times[mid] <= time) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo;
    }

    private T resolveIn(int i) {
        TrackKey<T> key = keys.get(i);
        return switch (key.inTangent()) {
            case Tangent.Explicit<T> explicit -> explicit.perTick();
            case Tangent.Flat<T> flat -> values.identity();
            case Tangent.Linear<T> linear -> i == 0 ? values.identity() : slope(i - 1, i);
            case Tangent.Auto<T> auto -> autoTangent(i, true);
        };
    }

    private T resolveOut(int i) {
        TrackKey<T> key = keys.get(i);
        return switch (key.outTangent()) {
            case Tangent.Explicit<T> explicit -> explicit.perTick();
            case Tangent.Flat<T> flat -> values.identity();
            case Tangent.Linear<T> linear -> i == keys.size() - 1 ? values.identity() : slope(i, i + 1);
            case Tangent.Auto<T> auto -> autoTangent(i, false);
        };
    }

    private T autoTangent(int i, boolean inSide) {
        boolean hasPrevious = i > 0;
        boolean hasNext = i < keys.size() - 1;
        if (!hasPrevious && !hasNext) {
            return values.identity();
        }
        TrackKey<T> key = keys.get(i);
        if (key.cut()) {
            if (inSide) {
                return hasPrevious ? slope(i - 1, i) : values.identity();
            }
            return hasNext ? slope(i, i + 1) : values.identity();
        }
        if (!hasPrevious) {
            return slope(i, i + 1);
        }
        if (!hasNext) {
            return slope(i - 1, i);
        }
        TrackKey<T> previous = keys.get(i - 1);
        TrackKey<T> next = keys.get(i + 1);
        T across = values.add(values.difference(previous.right(), key.left()), values.difference(key.right(), next.left()));
        return values.scale(across, 1.0 / (next.time() - previous.time()));
    }

    private T slope(int from, int to) {
        TrackKey<T> a = keys.get(from);
        TrackKey<T> b = keys.get(to);
        return values.scale(values.difference(a.right(), b.left()), 1.0 / (b.time() - a.time()));
    }

    public static final class Builder<T> {

        private final TrackValue<T> values;
        private final List<TrackKey<T>> keys = new ArrayList<>();
        private final List<Marker> markers = new ArrayList<>();
        private int holdTicks;

        private Builder(TrackValue<T> values) {
            this.values = Objects.requireNonNull(values, "values");
        }

        public Builder<T> key(TrackKey<T> key) {
            keys.add(Objects.requireNonNull(key, "key"));
            return this;
        }

        public Builder<T> hermite(double time, T value) {
            return key(TrackKey.hermite(time, value));
        }

        public Builder<T> hermite(double time, T value, Tangent<T> inTangent, Tangent<T> outTangent) {
            return key(TrackKey.hermite(time, value, inTangent, outTangent));
        }

        public Builder<T> eased(double time, T value, EasingFunction easing) {
            return key(TrackKey.eased(time, value, easing));
        }

        public Builder<T> stepped(double time, T value) {
            return key(TrackKey.stepped(time, value));
        }

        public Builder<T> cut(double time, T left, T right, Interpolation out) {
            return key(TrackKey.cut(time, left, right, out));
        }

        public Builder<T> marker(Marker marker) {
            markers.add(Objects.requireNonNull(marker, "marker"));
            return this;
        }

        /** Quantise sampling to {@code floor(t / n) * n} ("on twos" is 2). {@code 0} turns it off. */
        public Builder<T> holdTicks(int holdTicks) {
            if (holdTicks < 0) {
                throw new IllegalArgumentException("holdTicks cannot be negative");
            }
            this.holdTicks = holdTicks;
            return this;
        }

        public Track<T> build() {
            if (keys.isEmpty()) {
                throw new IllegalStateException("a track needs at least one key");
            }
            List<TrackKey<T>> sorted = new ArrayList<>(keys);
            sorted.sort(Comparator.comparingDouble(TrackKey::time));
            for (int i = 1; i < sorted.size(); i++) {
                if (sorted.get(i).time() == sorted.get(i - 1).time()) {
                    throw new IllegalStateException("two keys at time " + sorted.get(i).time()
                            + "; use a cut key for a discontinuity");
                }
            }
            List<Marker> sortedMarkers = new ArrayList<>(markers);
            sortedMarkers.sort(Comparator.comparingLong(Marker::tick));
            return new Track<>(values, sorted, sortedMarkers, holdTicks);
        }
    }
}
