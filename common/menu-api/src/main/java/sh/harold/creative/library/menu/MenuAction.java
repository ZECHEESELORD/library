package sh.harold.creative.library.menu;

@FunctionalInterface
public interface MenuAction {

    void execute(MenuContext context);
}
