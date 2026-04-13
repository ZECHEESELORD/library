package sh.harold.creative.library.menu;

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
