package mezz.jei.test;

import mezz.jei.api.helpers.ICodecHelper;
import mezz.jei.api.helpers.IColorHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.runtime.IEditModeConfig;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.library.config.EditModeConfig;
import mezz.jei.library.focus.FocusFactory;
import mezz.jei.library.helpers.CodecHelper;
import mezz.jei.library.ingredients.subtypes.SubtypeInterpreters;
import mezz.jei.library.ingredients.subtypes.SubtypeManager;
import mezz.jei.library.load.registration.IngredientManagerBuilder;
import mezz.jei.test.lib.TestColorHelper;
import mezz.jei.test.lib.TestIngredient;
import mezz.jei.test.lib.TestIngredientHelper;
import mezz.jei.test.lib.TestPlugin;
import net.minecraft.core.RegistryAccess;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class EditModeConfigStrictTest {
	private IIngredientManager ingredientManager;
	private EditModeConfig.FileSerializer serializer;
	private TestIngredientHelper ingredientHelper;

	@BeforeEach
	public void setup() throws IOException {
		TestPlugin testPlugin = new TestPlugin();

		SubtypeInterpreters subtypeInterpreters = new SubtypeInterpreters();
		SubtypeManager subtypeManager = new SubtypeManager(subtypeInterpreters);

		IColorHelper colorHelper = new TestColorHelper();
		IngredientManagerBuilder ingredientManagerBuilder = new IngredientManagerBuilder(subtypeManager, colorHelper);
		testPlugin.registerIngredients(ingredientManagerBuilder);
		this.ingredientManager = ingredientManagerBuilder.build();

		IFocusFactory focusFactory = new FocusFactory(ingredientManager);
		ICodecHelper codecHelper = new CodecHelper(ingredientManager, focusFactory);

		Path tempFile = Files.createTempFile("strict-blacklist-test", ".json");
		Files.deleteIfExists(tempFile);
		this.serializer = new EditModeConfig.FileSerializer(tempFile, RegistryAccess.EMPTY, codecHelper);
		this.ingredientHelper = new TestIngredientHelper();
	}

	@Test
	public void testStrictModeHidesOnlyExactMatch() {
		ITypedIngredient<TestIngredient> target = createTypedIngredient(new TestIngredient(0));
		ITypedIngredient<TestIngredient> other = createTypedIngredient(new TestIngredient(1));

		EditModeConfig config = new EditModeConfig(serializer, ingredientManager);
		config.addIngredientToConfigBlacklist(target, IEditModeConfig.HideMode.STRICT, ingredientHelper);

		Assertions.assertTrue(config.isIngredientOnConfigBlacklist(target, ingredientHelper));
		Assertions.assertFalse(config.isIngredientOnConfigBlacklist(other, ingredientHelper));
	}

	@Test
	public void testStrictModePersistsAndReloadsFromFile() {
		ITypedIngredient<TestIngredient> target = createTypedIngredient(new TestIngredient(0));

		EditModeConfig config = new EditModeConfig(serializer, ingredientManager);
		config.addIngredientToConfigBlacklist(target, IEditModeConfig.HideMode.STRICT, ingredientHelper);

		EditModeConfig reloaded = new EditModeConfig(serializer, ingredientManager);
		Assertions.assertTrue(reloaded.isIngredientOnConfigBlacklist(target, ingredientHelper));
	}

	@Test
	public void testShowIngredientRemovesStrictEntry() {
		ITypedIngredient<TestIngredient> target = createTypedIngredient(new TestIngredient(0));

		EditModeConfig config = new EditModeConfig(serializer, ingredientManager);
		config.addIngredientToConfigBlacklist(target, IEditModeConfig.HideMode.STRICT, ingredientHelper);
		Assertions.assertTrue(config.isIngredientOnConfigBlacklist(target, ingredientHelper));

		config.showIngredientUsingConfigFile(target, IEditModeConfig.HideMode.STRICT);
		Assertions.assertFalse(config.isIngredientOnConfigBlacklist(target, ingredientHelper));
	}

	@Test
	public void testUnrelatedIngredientsAreUnaffectedWhenNoStrictEntriesExist() {
		ITypedIngredient<TestIngredient> ingredient = createTypedIngredient(new TestIngredient(0));

		EditModeConfig config = new EditModeConfig(serializer, ingredientManager);
		Assertions.assertFalse(config.isIngredientOnConfigBlacklist(ingredient, ingredientHelper));
	}

	private ITypedIngredient<TestIngredient> createTypedIngredient(TestIngredient ingredient) {
		return ingredientManager.createTypedIngredient(TestIngredient.TYPE, ingredient, false)
			.orElseThrow();
	}
}
