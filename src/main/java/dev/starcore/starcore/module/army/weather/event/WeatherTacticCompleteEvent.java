package dev.starcore.starcore.module.army.weather.event;

import dev.starcore.starcore.module.army.weather.model.WeatherTacticResult;
import dev.starcore.starcore.module.army.weather.model.WeatherTacticType;
import dev.starcore.starcore.module.weather.model.WeatherType;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * 天气战术完成事件
 * 在天气战术执行完成后触发
 */
public class WeatherTacticCompleteEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final UUID nationId;
    private final UUID armyId;
    private final WeatherTacticType tacticType;
    private final WeatherType weatherDuringTactic;
    private final WeatherTacticResult result;
    private final boolean success;

    public WeatherTacticCompleteEvent(
        UUID nationId,
        UUID armyId,
        WeatherTacticType tacticType,
        WeatherType weatherDuringTactic,
        WeatherTacticResult result
    ) {
        this.nationId = nationId;
        this.armyId = armyId;
        this.tacticType = tacticType;
        this.weatherDuringTactic = weatherDuringTactic;
        this.result = result;
        this.success = result.success();
    }

    public UUID getNationId() {
        return nationId;
    }

    public UUID getArmyId() {
        return armyId;
    }

    public WeatherTacticType getTacticType() {
        return tacticType;
    }

    public WeatherType getWeatherDuringTactic() {
        return weatherDuringTactic;
    }

    public WeatherTacticResult getResult() {
        return result;
    }

    public boolean isSuccess() {
        return success;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
