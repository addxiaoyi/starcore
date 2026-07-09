package dev.starcore.starcore.foundation.animation;

import org.bukkit.plugin.Plugin;

/**
 * GUI 动画系统注册中心
 * 统一管理所有动画服务，方便其他模块获取
 */
public final class GuiAnimationRegistry {
    private static GuiAnimationRegistry instance;

    private final Plugin plugin;
    private final GuiAnimationManager guiAnimationManager;
    private final MenuTransitionAnimator menuTransitionAnimator;
    private final ParticleEffectManager particleEffectManager;
    private final SoundFeedbackManager soundFeedbackManager;
    private final LoadingAnimationManager loadingAnimationManager;
    private final AnimationPlayer animationPlayer;

    private GuiAnimationRegistry(Plugin plugin) {
        this.plugin = plugin;

        // 初始化所有动画管理器
        this.animationPlayer = new AnimationPlayer(plugin);
        this.soundFeedbackManager = new SoundFeedbackManager(plugin);
        this.guiAnimationManager = new GuiAnimationManager(plugin);
        this.particleEffectManager = new ParticleEffectManager(plugin);
        this.menuTransitionAnimator = new MenuTransitionAnimator(plugin, guiAnimationManager);
        this.loadingAnimationManager = new LoadingAnimationManager(plugin, guiAnimationManager, soundFeedbackManager);
    }

    /**
     * 初始化动画系统
     */
    public static synchronized GuiAnimationRegistry initialize(Plugin plugin) {
        if (instance == null) {
            instance = new GuiAnimationRegistry(plugin);
        }
        return instance;
    }

    /**
     * 获取动画系统实例
     */
    public static GuiAnimationRegistry getInstance() {
        if (instance == null) {
            throw new IllegalStateException("GuiAnimationRegistry not initialized. Call initialize() first.");
        }
        return instance;
    }

    /**
     * 获取 GUI 动画管理器
     */
    public GuiAnimationManager getGuiAnimationManager() {
        return guiAnimationManager;
    }

    /**
     * 获取菜单过渡动画器
     */
    public MenuTransitionAnimator getMenuTransitionAnimator() {
        return menuTransitionAnimator;
    }

    /**
     * 获取粒子效果管理器
     */
    public ParticleEffectManager getParticleEffectManager() {
        return particleEffectManager;
    }

    /**
     * 获取声音反馈管理器
     */
    public SoundFeedbackManager getSoundFeedbackManager() {
        return soundFeedbackManager;
    }

    /**
     * 获取加载动画管理器
     */
    public LoadingAnimationManager getLoadingAnimationManager() {
        return loadingAnimationManager;
    }

    /**
     * 获取原始动画播放器
     */
    public AnimationPlayer getAnimationPlayer() {
        return animationPlayer;
    }

    /**
     * 关闭所有动画（插件禁用时调用）
     */
    public void shutdown() {
        guiAnimationManager.cancelAllAnimations();
        menuTransitionAnimator.cancelAllTransitions();
        particleEffectManager.cancelAllEffects();
        loadingAnimationManager.cancelAll();
    }

    // ==================== 快捷方法 ====================

    /**
     * 播放菜单打开动画
     */
    public void playMenuOpen(org.bukkit.entity.Player player, String menuName) {
        guiAnimationManager.playMenuOpenAnimation(player, menuName);
    }

    /**
     * 播放菜单关闭动画
     */
    public void playMenuClose(org.bukkit.entity.Player player) {
        guiAnimationManager.playMenuCloseAnimation(player);
    }

    /**
     * 播放物品点击动画
     */
    public void playItemClick(org.bukkit.entity.Player player, org.bukkit.inventory.ItemStack item) {
        guiAnimationManager.playItemClickAnimation(player, item);
    }

    /**
     * 播放成功动画
     */
    public void playSuccess(org.bukkit.entity.Player player, String message) {
        guiAnimationManager.playSuccessSound(player);
        player.sendMessage(net.kyori.adventure.text.Component.text("§a✓ " + message));
    }

    /**
     * 播放失败动画
     */
    public void playFailure(org.bukkit.entity.Player player, String message) {
        guiAnimationManager.playFailureSound(player);
        player.sendMessage(net.kyori.adventure.text.Component.text("§c✗ " + message));
    }

    /**
     * 播放稀有物品效果
     */
    public void playRareItem(org.bukkit.entity.Player player, String itemName) {
        guiAnimationManager.playRareItemEffect(player, itemName);
    }

    /**
     * 播放粒子效果
     */
    public void playParticle(org.bukkit.entity.Player player, ParticleEffectManager.ParticlePreset preset) {
        particleEffectManager.playPreset(player, preset);
    }

    /**
     * 播放指定音效
     */
    public void playSound(org.bukkit.entity.Player player, SoundFeedbackManager.SoundType type) {
        soundFeedbackManager.play(player, type);
    }

    /**
     * 播放成功音效
     */
    public void playSuccessSound(org.bukkit.entity.Player player) {
        soundFeedbackManager.playSuccess(player);
    }

    /**
     * 播放失败音效
     */
    public void playFailureSound(org.bukkit.entity.Player player) {
        soundFeedbackManager.playFailure(player);
    }

    /**
     * 播放导航音效
     */
    public void playNavigateSound(org.bukkit.entity.Player player) {
        soundFeedbackManager.playNavigate(player);
    }

    /**
     * 播放选中音效
     */
    public void playSelectSound(org.bukkit.entity.Player player) {
        soundFeedbackManager.playSelect(player);
    }

    /**
     * 播放奖励音效
     */
    public void playRewardSound(org.bukkit.entity.Player player) {
        soundFeedbackManager.playReward(player);
    }

    /**
     * 开始旋转加载动画
     */
    public void startSpinner(org.bukkit.entity.Player player, String message, java.util.function.Consumer<Void> onComplete) {
        loadingAnimationManager.startSpinner(player, message, onComplete);
    }

    /**
     * 开始进度条加载动画
     */
    public void startProgress(org.bukkit.entity.Player player, String message, int steps,
                            java.util.function.Consumer<Integer> onStep,
                            java.util.function.Consumer<Void> onComplete) {
        loadingAnimationManager.startProgress(player, message, steps, onStep, onComplete);
    }

    /**
     * 取消玩家的加载动画
     */
    public void cancelLoading(org.bukkit.entity.Player player) {
        loadingAnimationManager.cancelLoader(player);
    }

    /**
     * 播放页面过渡
     */
    public void playTransition(org.bukkit.entity.Player player, org.bukkit.inventory.Inventory from,
                              org.bukkit.inventory.Inventory to,
                              java.util.function.Consumer<org.bukkit.inventory.Inventory> onComplete) {
        menuTransitionAnimator.playFadeTransition(player, from, to, onComplete);
    }
}
