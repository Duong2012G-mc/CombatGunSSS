package dev.duong2012g.combatgun.data;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class RecipeData {

    public enum RecipeKind {
        COMPONENT,
        AMMO,
        GUN,
        STATION
    }

    private final String id;
    private final String displayName;
    private final RecipeKind kind;
    private final Map<String, Integer> ingredients;
    private final int outputAmount;
    private final String stationId;

    public RecipeData(String id,
                      String displayName,
                      RecipeKind kind,
                      Map<String, Integer> ingredients,
                      int outputAmount,
                      String stationId) {
        this.id = id;
        this.displayName = displayName;
        this.kind = kind;
        this.ingredients = Collections.unmodifiableMap(new LinkedHashMap<>(ingredients));
        this.outputAmount = outputAmount;
        this.stationId = stationId;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public RecipeKind getKind() {
        return kind;
    }

    public Map<String, Integer> getIngredients() {
        return ingredients;
    }

    public int getOutputAmount() {
        return outputAmount;
    }

    public String getStationId() {
        return stationId;
    }
}
