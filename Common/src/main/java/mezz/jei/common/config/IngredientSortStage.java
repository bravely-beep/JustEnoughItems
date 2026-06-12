package mezz.jei.common.config;

import java.util.List;

public enum IngredientSortStage {
	MOD_NAME, INGREDIENT_TYPE, ALPHABETICAL, CREATIVE_MENU, TAG, ARMOR, MAX_DURABILITY, CUSTOM_ORDER;

	public static final List<IngredientSortStage> defaultStages = List.of(
		IngredientSortStage.MOD_NAME,
		IngredientSortStage.INGREDIENT_TYPE,
		IngredientSortStage.CREATIVE_MENU
	);
}
