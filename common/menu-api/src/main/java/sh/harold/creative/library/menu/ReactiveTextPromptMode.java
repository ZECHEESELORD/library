package sh.harold.creative.library.menu;

public enum ReactiveTextPromptMode {
    CHAT,
    SIGN,
    ANVIL,
    BOOK_AND_QUILL;

    public boolean fancy() {
        return this != CHAT;
    }
}
