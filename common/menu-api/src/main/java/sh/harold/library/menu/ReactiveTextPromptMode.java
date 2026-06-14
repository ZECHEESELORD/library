package sh.harold.library.menu;

public enum ReactiveTextPromptMode {
    PROMPT,
    CHAT,
    SIGN,
    ANVIL,
    BOOK_AND_QUILL;

    public boolean fancy() {
        return this != CHAT;
    }
}
