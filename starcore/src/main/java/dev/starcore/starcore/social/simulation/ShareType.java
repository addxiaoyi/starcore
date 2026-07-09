package dev.starcore.starcore.social.simulation;

/**
 * 分享类型
 */
public enum ShareType {
    FRIEND("好友分享"),
    PUBLIC("公开分享"),
    NATION("国家分享");

    private final String name;

    ShareType(String name) {
        this.name = name;
    }

    public String getName() { return name; }
}
