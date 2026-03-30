package sh.harold.creative.library.camera;

public enum EaseOutCurve {
    QUADRATIC {
        @Override
        public double apply(double remainingFraction) {
            return remainingFraction * remainingFraction;
        }
    },
    CUBIC {
        @Override
        public double apply(double remainingFraction) {
            return remainingFraction * remainingFraction * remainingFraction;
        }
    },
    QUINTIC {
        @Override
        public double apply(double remainingFraction) {
            double squared = remainingFraction * remainingFraction;
            return squared * squared * remainingFraction;
        }
    };

    public abstract double apply(double remainingFraction);
}
