package sh.harold.creative.library.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record ReactiveTextPromptRequest(
        String key,
        ReactiveTextPromptMode preferredMode,
        String prompt,
        String initialValue,
        List<String> signLines
) {

    public ReactiveTextPromptRequest {
        Objects.requireNonNull(key, "key");
        if (key.isBlank()) {
            throw new IllegalArgumentException("key cannot be blank");
        }
        preferredMode = Objects.requireNonNull(preferredMode, "preferredMode");
        Objects.requireNonNull(prompt, "prompt");
        if (prompt.isBlank()) {
            throw new IllegalArgumentException("prompt cannot be blank");
        }
        initialValue = initialValue == null ? "" : initialValue;
        signLines = immutableSignLines(signLines);
    }

    public static ReactiveTextPromptRequest chat(String key, String prompt, String initialValue) {
        return new ReactiveTextPromptRequest(key, ReactiveTextPromptMode.CHAT, prompt, initialValue, List.of());
    }

    public static ReactiveTextPromptRequest prompt(String key, String prompt, String initialValue) {
        return new ReactiveTextPromptRequest(key, ReactiveTextPromptMode.PROMPT, prompt, initialValue, List.of());
    }

    public static ReactiveTextPromptRequest sign(String key, String prompt, String initialValue) {
        String normalizedInitialValue = initialValue == null ? "" : initialValue;
        return new ReactiveTextPromptRequest(
                key,
                ReactiveTextPromptMode.SIGN,
                prompt,
                normalizedInitialValue,
                List.of(normalizedInitialValue, "^^^^^^", prompt, "")
        );
    }

    private static List<String> immutableSignLines(List<String> signLines) {
        if (signLines == null) {
            return List.of();
        }
        List<String> copy = new ArrayList<>(signLines.size());
        for (String line : signLines) {
            copy.add(line == null ? "" : line);
        }
        if (copy.size() > 4) {
            throw new IllegalArgumentException("signLines cannot contain more than four lines");
        }
        return List.copyOf(copy);
    }
}
