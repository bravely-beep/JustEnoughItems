package mezz.jei.test;

import mezz.jei.api.helpers.IColorHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.library.config.PinnedIngredientOrderConfig;
import mezz.jei.library.ingredients.subtypes.SubtypeInterpreters;
import mezz.jei.library.ingredients.subtypes.SubtypeManager;
import mezz.jei.library.load.registration.IngredientManagerBuilder;
import mezz.jei.test.lib.TestColorHelper;
import mezz.jei.test.lib.TestIngredient;
import mezz.jei.test.lib.TestPlugin;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

public class PinnedIngredientOrderConfigTest {
	private IIngredientManager ingredientManager;

	@BeforeEach
	public void setup() {
		TestPlugin testPlugin = new TestPlugin();

		SubtypeInterpreters subtypeInterpreters = new SubtypeInterpreters();
		SubtypeManager subtypeManager = new SubtypeManager(subtypeInterpreters);

		IColorHelper colorHelper = new TestColorHelper();
		IngredientManagerBuilder ingredientManagerBuilder = new IngredientManagerBuilder(subtypeManager, colorHelper);
		testPlugin.registerIngredients(ingredientManagerBuilder);
		this.ingredientManager = ingredientManagerBuilder.build();
	}

	@Test
	public void testPinnedIngredientsAreSortedByFileOrder() {
		ITypedIngredient<TestIngredient> first = createTypedIngredient(new TestIngredient(0));
		ITypedIngredient<TestIngredient> second = createTypedIngredient(new TestIngredient(1));

		PinnedIngredientOrderConfig config = new PinnedIngredientOrderConfig(new NullSerializer(), ingredientManager);
		config.setPinnedOrder(List.of(second, first));

		Assertions.assertEquals(0, config.getSortedIndex(second));
		Assertions.assertEquals(1, config.getSortedIndex(first));
	}

	@Test
	public void testUnpinnedIngredientsSortLast() {
		ITypedIngredient<TestIngredient> pinned = createTypedIngredient(new TestIngredient(0));
		ITypedIngredient<TestIngredient> unpinned = createTypedIngredient(new TestIngredient(1));

		PinnedIngredientOrderConfig config = new PinnedIngredientOrderConfig(new NullSerializer(), ingredientManager);
		config.setPinnedOrder(List.of(pinned));

		Assertions.assertEquals(0, config.getSortedIndex(pinned));
		Assertions.assertEquals(Integer.MAX_VALUE, config.getSortedIndex(unpinned));
	}

	@Test
	public void testDuplicateEntriesKeepTheFirstPosition() {
		ITypedIngredient<TestIngredient> ingredient = createTypedIngredient(new TestIngredient(0));
		ITypedIngredient<TestIngredient> other = createTypedIngredient(new TestIngredient(1));

		PinnedIngredientOrderConfig config = new PinnedIngredientOrderConfig(new NullSerializer(), ingredientManager);
		config.setPinnedOrder(List.of(ingredient, other, ingredient));

		Assertions.assertEquals(0, config.getSortedIndex(ingredient));
		Assertions.assertEquals(1, config.getSortedIndex(other));
	}

	private ITypedIngredient<TestIngredient> createTypedIngredient(TestIngredient ingredient) {
		return ingredientManager.createTypedIngredient(TestIngredient.TYPE, ingredient, false)
			.orElseThrow();
	}

	private static class NullSerializer implements PinnedIngredientOrderConfig.ISerializer {
		@Override
		public void initialize(PinnedIngredientOrderConfig config) {

		}

		@Override
		public void save(PinnedIngredientOrderConfig config) {

		}

		@Override
		public void load(PinnedIngredientOrderConfig config) {

		}
	}
}
