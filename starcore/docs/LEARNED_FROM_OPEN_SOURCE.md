# StarCore 开源插件学习成果

## 分析的开源项目

| 项目 | 特点 | 适用场景 |
|------|------|----------|
| Towns-and-Nations | 现代GUI设计、TriumphGUI | 菜单系统 |
| TownyMenus | 配置驱动菜单 | 菜单配置 |
| DeluxeMenus | YAML配置菜单 | 简单菜单 |
| GeyserMC | 基岩版支持 | 跨平台 |

## 学习的最佳实践

### 1. IconManager 模式 (Towns-and-Nations)
```java
// 统一的图标管理器
public class IconManager {
    private final Map<IconKey, ItemBuilder> icons;
    
    public ItemBuilder get(IconKey key) {
        return icons.get(key).clone();
    }
}

public enum IconKey {
    NATION_ICON,
    WAR_ICON,
    DIPLOMACY_ICON
}
```

### 2. Lang 国际化系统
```java
public enum Lang {
    GUI_NATION_BUTTON("§6国家"),
    GUI_TOWN_BUTTON("§6城镇"),
    GUI_WAR_BUTTON("§c战争");
}
```

### 3. BasicGui 抽象基类
```java
public abstract class BasicGui {
    protected final Gui gui;
    protected final Player player;
    
    public abstract void open();
    
    public static GuiItem createBackArrow(...) {}
}
```

### 4. 菜单构建最佳实践
- 使用 `gui.setItem(row, col, item)` 而非 `setItem(slot, item)`
- 统一处理点击事件：`event.setCancelled(true)`
- 使用 `ItemBuilder` 链式构建物品

## 改进 StarCore 的建议

### 高优先级
1. **重构 TriumphNationMenu** - 参考 BasicGui 模式
2. **添加国际化支持** - 使用 Lang 枚举
3. **统一图标管理** - 创建 IconManager

### 中优先级
1. **添加图标缓存** - 避免重复构建
2. **统一事件处理** - 抽象点击事件

### 低优先级
1. **配置驱动菜单** - 参考 DeluxeMenus
2. **添加 Geyser 支持** - 基岩版互通

## 文件位置

- Towns-and-Nations: `open-source-learn/Towns-and-Nations`
- TownyMenus: `open-source-learn/TownyMenus`
- DeluxeMenus: `open-source-learn/DeluxeMenus`
- Geyser: `open-source-learn/Geyser`
