package sh.harold.creative.library.message;

import java.util.Objects;

public sealed interface Click permits Click.OpenUrl, Click.RunCommand, Click.SuggestCommand, Click.CopyToClipboard, Click.ChangePage {

    static Click openUrl(String url) {
        return new OpenUrl(url);
    }

    static Click runCommand(String command) {
        return new RunCommand(command);
    }

    static Click suggestCommand(String command) {
        return new SuggestCommand(command);
    }

    static Click copyToClipboard(String value) {
        return new CopyToClipboard(value);
    }

    static Click changePage(int page) {
        return new ChangePage(page);
    }

    record OpenUrl(String url) implements Click {

        public OpenUrl {
            requireText(url, "url");
        }
    }

    record RunCommand(String command) implements Click {

        public RunCommand {
            requireText(command, "command");
        }
    }

    record SuggestCommand(String command) implements Click {

        public SuggestCommand {
            requireText(command, "command");
        }
    }

    record CopyToClipboard(String value) implements Click {

        public CopyToClipboard {
            requireText(value, "value");
        }
    }

    record ChangePage(int page) implements Click {

        public ChangePage {
            if (page <= 0) {
                throw new IllegalArgumentException("page must be greater than 0");
            }
        }
    }

    private static void requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
    }
}
