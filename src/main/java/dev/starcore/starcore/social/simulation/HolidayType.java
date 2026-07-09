package dev.starcore.starcore.social.simulation;

/**
 * 节日类型
 */
public enum HolidayType {
    NEW_YEAR("元旦", 1, 1, 7),
    SPRING_FESTIVAL("春节", 1, 15, 15),
    VALENTINE("情人节", 2, 14, 1),
    LABOR_DAY("劳动节", 5, 1, 7),
    NATIONAL_DAY("国庆节", 10, 1, 7),
    CHRISTMAS("圣诞节", 12, 25, 1),
    ANNIVERSARY("周年庆", 0, 0, 7);

    private final String name;
    private final int month;
    private final int day;
    private final int duration;

    HolidayType(String name, int month, int day, int duration) {
        this.name = name;
        this.month = month;
        this.day = day;
        this.duration = duration;
    }

    public String getName() { return name; }
    public int getMonth() { return month; }
    public int getDay() { return day; }
    public int getDuration() { return duration; }
}
