package dev.starcore.starcore.module.army.weather.event;

import dev.starcore.starcore.module.army.weather.model.WeatherTacticResult;
import dev.starcore.starcore.module.army.weather.model.WeatherTacticState;
import dev.starcore.starcore.module.army.weather.model.WeatherTacticType;
import dev.starcore.starcore.module.weather.model.WeatherType;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * 天气战术执行事件
 * 在天气战术执行前触发，可取消
 */
public class WeatherTacticExecuteEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();

    private final UUID nationId;
    private final UUID armyId;
    private final WeatherTacticType tacticType;
    private final WeatherType currentWeather;
    private WeatherTacticState state;
    private boolean cancelled;
    private String cancelReason;

    public WeatherTacticExecuteEvent(
        UUID nationId,
        UUID armyId,
        WeatherTacticType tacticType,
        WeatherType currentWeather,
        WeatherTacticState state
    ) {
        this.nationId = nationId;
        this.armyId = armyId;
        this.tacticType = tacticType;
        this.currentWeather = currentWeather;
        this.state = state;
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

    public WeatherType getCurrentWeather() {
        return currentWeather;
    }

    public WeatherTacticState getState() {
        return state;
    }

    public void setState(WeatherTacticState state) {
        this.state = state;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
