package dev.duong2012g.combatgun.data;

public class AmmoTypeData {

    private final String id;
    private final String displayName;
    private final int output;
    private final RecipeData recipe;

    public AmmoTypeData(String id, String displayName, int output, RecipeData recipe) {
        this.id = id;
        this.displayName = displayName;
        this.output = output;
        this.recipe = recipe;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getOutput() {
        return output;
    }

    public RecipeData getRecipe() {
        return recipe;
    }
}
