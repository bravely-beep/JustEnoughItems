package mezz.jei.gui.ingredients;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.platform.Services;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Loads a user-authored list of ingredient keys to hide from the JEI ingredient list from
 * {@code <config>/jei/ingredient-hidden.json}, and hides them in addition to JEI's own
 * edit-mode blacklist.
 *
 * <p>This exists because JEI's blacklist matches by grouping/unique id, which cannot isolate
 * fluid variants that collapse to the base fluid id (notably Create's empty "Uncraftable Potion"
 * fluid, whose single id equals the grouping id shared by every {@code create:potion} variant —
 * blacklisting it hides them all). This list matches by the same component-aware keys as the
 * custom ordering, so {@code create:potion?potion=minecraft:empty} hides only the empty variant.
 *
 * <p>File format: a plain JSON array of keys, e.g.
 * <pre>
 * [
 *   "create:potion?potion=minecraft:water",
 *   "create:potion?potion=minecraft:empty"
 * ]
 * </pre>
 * The file is re-read only when its modification time changes.
 */
public final class CustomHiddenIngredients {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final String FILE_NAME = "ingredient-hidden.json";

	private static final Hidden EMPTY = new Hidden(Set.of(), false);

	private static Hidden cached = EMPTY;
	private static long cachedModified = Long.MIN_VALUE;

	private CustomHiddenIngredients() {}

	/** @return an immutable snapshot of the hidden keys. Safe to call from any thread. */
	public static synchronized Hidden get() {
		Path path = resolvePath();
		if (path == null) {
			return cached;
		}
		try {
			if (!Files.exists(path)) {
				writeTemplate(path);
				cached = EMPTY;
				cachedModified = Files.exists(path) ? Files.getLastModifiedTime(path).toMillis() : Long.MIN_VALUE;
				return cached;
			}
			long modified = Files.getLastModifiedTime(path).toMillis();
			if (modified != cachedModified) {
				cached = load(path);
				cachedModified = modified;
			}
		} catch (IOException e) {
			LOGGER.error("Failed to read JEI custom hidden ingredients file: {}", path, e);
		}
		return cached;
	}

	private static Path resolvePath() {
		try {
			return Services.PLATFORM.getConfigHelper()
				.createJeiConfigDir()
				.resolve(FILE_NAME);
		} catch (RuntimeException e) {
			LOGGER.error("Failed to resolve JEI config directory for custom hidden ingredients", e);
			return null;
		}
	}

	private static Hidden load(Path path) {
		try (Reader reader = Files.newBufferedReader(path)) {
			JsonElement root = JsonParser.parseReader(reader);
			if (root == null || !root.isJsonArray()) {
				LOGGER.error("JEI custom hidden ingredients file must be a JSON array of strings: {}", path);
				return EMPTY;
			}
			JsonArray array = root.getAsJsonArray();
			java.util.Set<String> keys = new java.util.HashSet<>(array.size());
			boolean needsPotionContents = false;
			for (JsonElement element : array) {
				if (!element.isJsonPrimitive()) {
					continue;
				}
				String key = element.getAsString().trim();
				if (key.isEmpty() || key.startsWith("//")) {
					continue;
				}
				keys.add(key);
				if (key.contains(CustomIngredientKeys.POTION_SELECTOR)) {
					needsPotionContents = true;
				}
			}
			return new Hidden(Set.copyOf(keys), needsPotionContents);
		} catch (IOException | RuntimeException e) {
			LOGGER.error("Failed to parse JEI custom hidden ingredients file: {}", path, e);
			return EMPTY;
		}
	}

	private static void writeTemplate(Path path) {
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, "[\n]\n");
			LOGGER.info("Created empty JEI custom hidden ingredients file: {}", path);
		} catch (IOException e) {
			LOGGER.error("Failed to create JEI custom hidden ingredients template: {}", path, e);
		}
	}

	/** Immutable set of hidden keys plus the test for whether an ingredient is hidden. */
	public static final class Hidden {
		private final Set<String> keys;
		private final boolean needsPotionContents;

		private Hidden(Set<String> keys, boolean needsPotionContents) {
			this.keys = keys;
			this.needsPotionContents = needsPotionContents;
		}

		public boolean isEmpty() {
			return keys.isEmpty();
		}

		public <V> boolean isHidden(ITypedIngredient<V> typedIngredient, IIngredientManager ingredientManager) {
			if (keys.isEmpty()) {
				return false;
			}
			V ingredient = typedIngredient.getIngredient();
			String resourceLocation;
			try {
				resourceLocation = ingredientManager.getIngredientHelper(ingredient)
					.getResourceLocation(ingredient)
					.toString();
			} catch (RuntimeException e) {
				return false;
			}
			List<String> candidateKeys = CustomIngredientKeys.candidateKeys(resourceLocation, ingredient, needsPotionContents);
			for (String key : candidateKeys) {
				if (keys.contains(key)) {
					return true;
				}
			}
			return false;
		}
	}
}
