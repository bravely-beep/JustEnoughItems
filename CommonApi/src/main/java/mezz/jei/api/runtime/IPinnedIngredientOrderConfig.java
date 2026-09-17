package mezz.jei.api.runtime;

import mezz.jei.api.ingredients.ITypedIngredient;
import org.jetbrains.annotations.ApiStatus;

/**
 * Gives access to the pinned ingredient order config, which lets players pin specific
 * ingredients to a fixed position in JEI's ingredient list by editing a config file.
 *
 * @since 19.56.0
 */
@ApiStatus.NonExtendable
public interface IPinnedIngredientOrderConfig {
	/**
	 * Returns the pinned sort index for the given ingredient, if it has been pinned in the config file.
	 * Returns {@link Integer#MAX_VALUE} if the ingredient has not been pinned.
	 *
	 * @since 19.56.0
	 */
	<V> int getSortedIndex(ITypedIngredient<V> ingredient);
}
