package sh.harold.creative.library.menu;

public enum ActionVerb {

    VIEW("view"),
    OPEN("open"),
    BROWSE("browse"),
    SELECT("select"),
    BUY("buy"),
    CLAIM("claim"),
    CONFIRM("confirm"),
    TOGGLE("toggle"),
    MANAGE("manage"),
    CLOSE("close"),
    BACK("go back"),
    PREVIOUS_PAGE("go to previous page"),
    NEXT_PAGE("go to next page"),
    SWITCH_TAB("view");

    private final String promptLabel;

    ActionVerb(String promptLabel) {
        this.promptLabel = promptLabel;
    }

    public String promptLabel() {
        return promptLabel;
    }
}
