package dev.starcore.starcore.module.nation.gui;

import org.bukkit.entity.Player;

import java.util.function.Consumer;

/**
 * Anvil 输入回调接口
 */
@FunctionalInterface
public interface AnvilInputCallback {
    /**
     * 处理 Anvil 输入
     *
     * @param input 用户输入的文本
     */
    void onInput(String input);

    /**
     * 创建带取消处理的回调
     */
    static AnvilInputHandler handler(Consumer<String> onConfirm, Runnable onCancel) {
        return new AnvilInputHandler(onConfirm, onCancel);
    }

    /**
     * Anvil 输入处理器
     */
    class AnvilInputHandler {
        private final Consumer<String> onConfirm;
        private final Runnable onCancel;

        public AnvilInputHandler(Consumer<String> onConfirm, Runnable onCancel) {
            this.onConfirm = onConfirm;
            this.onCancel = onCancel;
        }

        public void handleConfirm(String input) {
            if (onConfirm != null) {
                onConfirm.accept(input);
            }
        }

        public void handleCancel() {
            if (onCancel != null) {
                onCancel.run();
            }
        }
    }
}
