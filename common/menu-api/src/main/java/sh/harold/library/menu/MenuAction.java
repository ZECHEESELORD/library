package sh.harold.library.menu;

@FunctionalInterface
public interface MenuAction {

    void execute(MenuContext context);
}
