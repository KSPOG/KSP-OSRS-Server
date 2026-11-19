import net.runelite.api.MenuAction;
import net.runelite.api.ScriptEvent;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.widgets.Widget;

/** Bridges the native Game client layout dropdown to the local server. */
public final class LocalDisplaySettings {
    private static final int SETTINGS = 134;
    private static final int DROPDOWN_BUTTONS = SETTINGS << 16 | 29;
    private static final int SELECT_DROPDOWN_OPTION = 3852;
    private static final int GAME_LAYOUT_MENU = 3509;

    private static int lastChoice = -1;
    private static long lastReported;
    private LocalDisplaySettings() {}

    public static void onScriptExecution(Object event) {
        if (event instanceof ScriptEvent script) {
            int choice = layoutChoice(script.getArguments());
            if (choice >= 0) report(choice);
        }
    }

    public static void onMenuAction(Object event) {
        if (!(event instanceof MenuOptionClicked click) || click.isConsumed() || !isSettingsSelection(click)) {
            return;
        }
        Widget row = click.getWidget();
        int choice = layoutChoice(row == null ? null : row.getOnOpListener());
        if (choice < 0 && click.getParam1() == DROPDOWN_BUTTONS && click.getParam0() >= 2) {
            Widget buttons = bf.cr.getWidget(DROPDOWN_BUTTONS);
            Widget label = buttons == null ? null : buttons.getChild(click.getParam0() - 1);
            choice = label == null ? -1 : layoutLabel(label.getText());
        }
        if (choice >= 0) report(choice);
    }

    private static void report(int choice) {
        long now = System.nanoTime();
        if (choice == lastChoice && now - lastReported < 250_000_000L) return;
        lastChoice = choice;
        lastReported = now;
        client.mi("ksp-layout " + choice);
        System.out.println("KSP_LOCAL_DISPLAY_SELECTED mode=" + choice);
    }

    static boolean isSettingsSelection(MenuOptionClicked click) {
        return click.getParam1() >>> 16 == SETTINGS
            && "Select".equals(click.getMenuOption())
            && (click.getMenuAction() == MenuAction.CC_OP || click.getMenuAction() == MenuAction.CC_OP_LOW_PRIORITY);
    }

    static int layoutChoice(Object[] listener) {
        if (listener == null || listener.length < 3 || !Integer.valueOf(SELECT_DROPDOWN_OPTION).equals(listener[0])
            || !(listener[2] instanceof String label)) {
            return -1;
        }
        if (listener.length == 16 && Integer.valueOf(GAME_LAYOUT_MENU).equals(listener[11])
            && Integer.valueOf(12).equals(listener[12]) && Integer.valueOf(2732).equals(listener[15])
            && listener[4] instanceof Integer selection && selection >= 0 && selection <= 2) {
            return selection;
        }
        // The bare menu event may supply the native title even when it lacks complete script arguments.
        return layoutLabel(label);
    }

    static int layoutLabel(String label) {
        if (label == null) return -1;
        return switch (label.replaceAll("<[^>]*>", "").strip()) {
            case "Fixed - Classic layout" -> 0;
            case "Resizable - Classic layout" -> 1;
            case "Resizable - Modern layout" -> 2;
            default -> -1;
        };
    }
}
