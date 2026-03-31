package sh.harold.creative.library.tween;

public enum Easing {
    LINEAR {
        @Override
        public double apply(double u) {
            return u;
        }
    },
    SMOOTHSTEP {
        @Override
        public double apply(double u) {
            return u * u * (3.0 - (2.0 * u));
        }
    },
    SMOOTHERSTEP {
        @Override
        public double apply(double u) {
            return u * u * u * ((u * ((u * 6.0) - 15.0)) + 10.0);
        }
    },
    QUAD_IN {
        @Override
        public double apply(double u) {
            return u * u;
        }
    },
    QUAD_OUT {
        @Override
        public double apply(double u) {
            double inverse = 1.0 - u;
            return 1.0 - (inverse * inverse);
        }
    },
    QUAD_IN_OUT {
        @Override
        public double apply(double u) {
            return u < 0.5 ? 2.0 * u * u : 1.0 - Math.pow(-2.0 * u + 2.0, 2.0) / 2.0;
        }
    },
    CUBIC_IN {
        @Override
        public double apply(double u) {
            return u * u * u;
        }
    },
    CUBIC_OUT {
        @Override
        public double apply(double u) {
            return 1.0 - Math.pow(1.0 - u, 3.0);
        }
    },
    CUBIC_IN_OUT {
        @Override
        public double apply(double u) {
            return u < 0.5 ? 4.0 * u * u * u : 1.0 - Math.pow(-2.0 * u + 2.0, 3.0) / 2.0;
        }
    },
    QUINT_IN {
        @Override
        public double apply(double u) {
            return Math.pow(u, 5.0);
        }
    },
    QUINT_OUT {
        @Override
        public double apply(double u) {
            return 1.0 - Math.pow(1.0 - u, 5.0);
        }
    },
    QUINT_IN_OUT {
        @Override
        public double apply(double u) {
            return u < 0.5 ? 16.0 * Math.pow(u, 5.0) : 1.0 - Math.pow(-2.0 * u + 2.0, 5.0) / 2.0;
        }
    },
    EXPO_IN {
        @Override
        public double apply(double u) {
            return u == 0.0 ? 0.0 : Math.pow(2.0, (10.0 * u) - 10.0);
        }
    },
    EXPO_OUT {
        @Override
        public double apply(double u) {
            return u == 1.0 ? 1.0 : 1.0 - Math.pow(2.0, -10.0 * u);
        }
    },
    EXPO_IN_OUT {
        @Override
        public double apply(double u) {
            if (u == 0.0 || u == 1.0) {
                return u;
            }
            return u < 0.5
                    ? Math.pow(2.0, 20.0 * u - 10.0) / 2.0
                    : (2.0 - Math.pow(2.0, -20.0 * u + 10.0)) / 2.0;
        }
    },
    BACK {
        @Override
        public double apply(double u) {
            double c1 = 1.70158;
            double c3 = c1 + 1.0;
            return (c3 * u * u * u) - (c1 * u * u);
        }
    },
    ELASTIC {
        @Override
        public double apply(double u) {
            if (u == 0.0 || u == 1.0) {
                return u;
            }
            double c4 = (2.0 * Math.PI) / 3.0;
            return -Math.pow(2.0, 10.0 * u - 10.0) * Math.sin((u * 10.0 - 10.75) * c4);
        }
    },
    BOUNCE {
        @Override
        public double apply(double u) {
            double n1 = 7.5625;
            double d1 = 2.75;
            if (u < 1.0 / d1) {
                return n1 * u * u;
            }
            if (u < 2.0 / d1) {
                double t = u - 1.5 / d1;
                return n1 * t * t + 0.75;
            }
            if (u < 2.5 / d1) {
                double t = u - 2.25 / d1;
                return n1 * t * t + 0.9375;
            }
            double t = u - 2.625 / d1;
            return n1 * t * t + 0.984375;
        }
    };

    public abstract double apply(double u);
}
