package sh.harold.library.example.feel.gametest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Collects every assertion's expected and actual values instead of stopping at the first failure, so one run reports
 * every mismatch between 02 and the implementation. {@link #finish} writes the table and fails the test if any row
 * failed.
 */
final class FeelChecks {

    record Result(String move, String assertion, String expected, String actual, boolean pass, String ref,
                  boolean note) {
    }

    private final List<Result> results = new ArrayList<>();

    void check(String move, String assertion, String expected, String actual, boolean pass, String ref) {
        Result result = new Result(move, assertion, expected, actual, pass, ref, false);
        results.add(result);
        System.out.println("FEEL-CHECK " + (pass ? "PASS" : "FAIL") + " | " + move + " | " + assertion
                + " | expected " + expected + " | actual " + actual + " | " + ref);
    }

    void near(String move, String assertion, double expected, double actual, double tolerance, String ref) {
        boolean pass = Double.isFinite(actual) && Math.abs(actual - expected) <= tolerance;
        check(move, assertion, fmt(expected) + " +/- " + fmt(tolerance), fmt(actual), pass, ref);
    }

    void equal(String move, String assertion, long expected, long actual, String ref) {
        check(move, assertion, Long.toString(expected), Long.toString(actual), expected == actual, ref);
    }

    /** An observation with no pass condition: recorded as {@code note}, never fails the test. */
    void note(String move, String observation, String reading, String actual, String ref) {
        results.add(new Result(move, observation, reading, actual, true, ref, true));
        System.out.println("FEEL-CHECK NOTE | " + move + " | " + observation + " | " + reading + " | actual "
                + actual + " | " + ref);
    }

    /** Records a scenario that could not run (a crash or a precondition that never happened). */
    void broken(String move, String assertion, Throwable error) {
        check(move, assertion, "scenario runs", error.getClass().getSimpleName() + ": " + error.getMessage(), false,
                "-");
        error.printStackTrace(System.out);
    }

    static String fmt(double value) {
        if (Double.isNaN(value)) {
            return "NaN";
        }
        return String.format(Locale.ROOT, "%.4f", value);
    }

    long failures() {
        return results.stream().filter(result -> !result.pass()).count();
    }

    void finish(Path dir) {
        StringBuilder csv = new StringBuilder("move,assertion,expected,actual,result,ref\n");
        StringBuilder md = new StringBuilder("| Move | Assertion | Expected | Actual | Result | CSV row |\n"
                + "|---|---|---|---|---|---|\n");
        for (Result r : results) {
            csv.append(quote(r.move())).append(',').append(quote(r.assertion())).append(',')
                    .append(quote(r.expected())).append(',').append(quote(r.actual())).append(',')
                    .append(label(r, "FAIL")).append(',').append(quote(r.ref())).append('\n');
            md.append("| ").append(r.move()).append(" | ").append(r.assertion()).append(" | ").append(r.expected())
                    .append(" | ").append(r.actual()).append(" | ").append(label(r, "**FAIL**"))
                    .append(" | ").append(r.ref()).append(" |\n");
        }
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("results.csv"), csv);
            Files.writeString(dir.resolve("results.md"), md);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        long failed = failures();
        long notes = results.stream().filter(Result::note).count();
        System.out.println("FEEL-RESULTS " + (results.size() - failed - notes) + " passed, " + failed + " failed, "
                + notes + " notes; "
                + dir.toAbsolutePath());
        if (failed > 0) {
            throw new AssertionError(failed + " feel assertion(s) failed; see " + dir.resolve("results.md"));
        }
    }

    private static String label(Result r, String fail) {
        return r.note() ? "note" : r.pass() ? "pass" : fail;
    }

    private static String quote(String text) {
        if (text.indexOf(',') < 0 && text.indexOf('"') < 0) {
            return text;
        }
        return '"' + text.replace("\"", "\"\"") + '"';
    }
}
