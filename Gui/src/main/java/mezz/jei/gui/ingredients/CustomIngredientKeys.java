package mezz.jei.gui.ingredients;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentHolder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.alchemy.PotionContents;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Shared key logic for the file-driven custom ordering and hiding features. Computes the set of
 * keys an ingredient can be matched by, so both features use identical matching.
 *
 * <p>Key forms:
 * <ul>
 *   <li>{@code <registry id>} — e.g. {@code create:potion}, {@code minecraft:water};</li>
 *   <li>{@code <registry id>?potion=<potion id>} — selects the variant whose
 *       {@code minecraft:potion_contents} names that potion. {@code minecraft:empty} additionally
 *       matches a variant that carries no potion contents at all (e.g. Create's empty
 *       "Uncraftable Potion" fluid), which has no other stable selector.</li>
 * </ul>
 */
public final class CustomIngredientKeys {
	/** Separator between a registry id and a potion id in a potion-variant key. */
	public static final String POTION_SELECTOR = "?potion=";

	/** Potion id used for variants with empty or absent potion contents. */
	public static final String EMPTY_POTION = "minecraft:empty";

	private CustomIngredientKeys() {}

	/**
	 * @param resourceLocation the ingredient's registry id, as a string
	 * @param ingredient       the raw ingredient object (ItemStack, FluidStack, ...)
	 * @param includePotion    whether any configured key uses the potion selector (skip the
	 *                         component read entirely when false)
	 * @return the keys this ingredient can match, most specific first
	 */
	public static List<String> candidateKeys(String resourceLocation, Object ingredient, boolean includePotion) {
		if (includePotion) {
			String potionId = potionId(ingredient);
			if (potionId != null) {
				return List.of(resourceLocation + POTION_SELECTOR + potionId, resourceLocation);
			}
		}
		return List.of(resourceLocation);
	}

	// Both ItemStack and (NeoForge) FluidStack implement the vanilla DataComponentHolder, so this
	// reads potions off items and fluids alike without any mod- or loader-specific dependency.
	// Returns EMPTY_POTION when the component is present-but-empty or entirely absent, and null
	// for ingredients that cannot carry components at all.
	@Nullable
	private static String potionId(Object ingredient) {
		if (ingredient instanceof DataComponentHolder holder) {
			PotionContents potionContents = holder.get(DataComponents.POTION_CONTENTS);
			if (potionContents == null) {
				return EMPTY_POTION;
			}
			return potionContents.potion()
				.flatMap(Holder::unwrapKey)
				.map(ResourceKey::location)
				.map(ResourceLocation::toString)
				.orElse(EMPTY_POTION);
		}
		return null;
	}
}
