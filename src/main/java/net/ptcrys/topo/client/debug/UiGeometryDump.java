package net.ptcrys.topo.client.debug;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import java.util.Locale;

/**
 * 通用 ModularUI 几何取证:把任意元素树里带 id 的节点按层级缩进 dump 成文本(相对父系坐标 +
 * 尺寸 + 隐藏标记)。截图看不出的塌缩/越界/负坐标在这里直接现形——任何 UI 探针拿到
 * {@code screen.getMenu().getModularUI().ui.rootElement} 后即可调用,不限领域。
 */
public final class UiGeometryDump {

    private UiGeometryDump() {}

    /** Dump 整棵子树(仅打印带 id 的节点;无 id 的中间节点下钻但不占行)。 */
    public static String dump(UIElement root) {
        StringBuilder out = new StringBuilder("geometry (x/y relative to parent):\n");
        append(out, root, 0);
        return out.toString();
    }

    private static void append(StringBuilder out, UIElement element, int depth) {
        String id = element.getId();
        int childDepth = depth;
        if (id != null && !id.isEmpty()) {
            out.append("  ".repeat(depth + 1))
                    .append(String.format(Locale.ROOT, "%s: x=%.1f y=%.1f w=%.1f h=%.1f%s%n",
                            id, element.getLayoutX(), element.getLayoutY(),
                            element.getSizeWidth(), element.getSizeHeight(),
                            element.isVisible() ? "" : " [hidden]"));
            childDepth = depth + 1;
        }
        for (UIElement child : element.getChildren()) {
            append(out, child, childDepth);
        }
    }
}
