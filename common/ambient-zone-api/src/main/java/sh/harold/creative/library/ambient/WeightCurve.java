package sh.harold.creative.library.ambient;

public enum WeightCurve {
    LINEAR {
        @Override
        public double apply(double value) {
            return value;
        }
    },
    SMOOTHSTEP {
        @Override
        public double apply(double value) {
            return value * value * (3.0 - (2.0 * value));
        }
    },
    EXPONENTIAL {
        @Override
        public double apply(double value) {
            return value <= 0.0 ? 0.0 : Math.pow(value, 2.0);
        }
    };

    public abstract double apply(double value);
}
