package mezz.jei.gui.ingredients;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import mezz.jei.common.platform.Services;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads a user-authored, ordered list of ingredient keys from
 * {@code <config>/jei/ingredient-order.json} and ranks ingredients by it.
 *
 * <p>File format: a plain JSON array of keys, most-important first.
 * <pre>
 * [
 *   "minecraft:water",
 *   "create:potion?potion=minecraft:awkward",
 *   "create:potion?potion=minecraft:healing",
 *   "create:potion"
 * ]
 * </pre>
 *
 * <p>Two key forms are supported:
 * <ul>
 *   <li><b>registry id</b> (e.g. {@code minecraft:water}, {@code create:potion}) — matches every
 *       stack/variant that shares that id;</li>
 *   <li><b>potion variant</b> {@code <registry id>?potion=<potion id>}
 *       (e.g. {@code create:potion?potion=minecraft:awkward}) — matches only the variant whose
 *       {@code minecraft:potion_contents} component names that potion. This distinguishes
 *       ingredients that share a registry id but differ by potion, such as Create's
 *       {@code create:potion} fluids. The potion id is the registry id of the potion
 *       (e.g. {@code minecraft:strong_healing}, {@code minecraft:long_swiftness}).</li>
 * </ul>
 *
 * <p>An ingredient's rank is the position of the first (top-most) key it matches; a more specific
 * potion-variant key placed above the bare registry id therefore wins. Ingredients matching no key
 * are {@link #UNRANKED} and left for later sort stages. The file is re-read only when its
 * modification time changes.
 */
public final class CustomIngredientOrder {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final String FILE_NAME = "ingredient-order.json";

	/** Rank for ingredients that match no key; keeps them equal so later stages decide. */
	public static final int UNRANKED = Integer.MAX_VALUE;

	private static final Order EMPTY = new Order(Map.of(), false);

	private static Order cachedOrder = EMPTY;
	private static long cachedModified = Long.MIN_VALUE;

	private CustomIngredientOrder() {}

	/**
	 * @return an immutable snapshot of the configured order. Safe to call from any thread.
	 */
	public static synchronized Order get() {
		Path path = resolvePath();
		if (path == null) {
			return cachedOrder;
		}
		try {
			if (!Files.exists(path)) {
				writeTemplate(path);
				cachedOrder = EMPTY;
				cachedModified = Files.exists(path) ? Files.getLastModifiedTime(path).toMillis() : Long.MIN_VALUE;
				return cachedOrder;
			}
			long modified = Files.getLastModifiedTime(path).toMillis();
			if (modified != cachedModified) {
				cachedOrder = load(path);
				cachedModified = modified;
			}
		} catch (IOException e) {
			LOGGER.error("Failed to read JEI custom ingredient order file: {}", path, e);
		}
		return cachedOrder;
	}

	private static Path resolvePath() {
		try {
			return Services.PLATFORM.getConfigHelper()
				.createJeiConfigDir()
				.resolve(FILE_NAME);
		} catch (RuntimeException e) {
			LOGGER.error("Failed to resolve JEI config directory for custom ingredient order", e);
			return null;
		}
	}

	private static Order load(Path path) {
		try (Reader reader = Files.newBufferedReader(path)) {
			JsonElement root = JsonParser.parseReader(reader);
			if (root == null || !root.isJsonArray()) {
				LOGGER.error("JEI custom ingredient order file must be a JSON array of strings: {}", path);
				return EMPTY;
			}
			JsonArray array = root.getAsJsonArray();
			Map<String, Integer> ranks = new LinkedHashMap<>(array.size());
			boolean needsPotionContents = false;
			int index = 0;
			for (JsonElement element : array) {
				if (!element.isJsonPrimitive()) {
					continue;
				}
				String key = element.getAsString().trim();
				if (key.isEmpty() || key.startsWith("//")) {
					continue;
				}
				if (!ranks.containsKey(key)) {
					ranks.put(key, index++);
					if (key.contains(CustomIngredientKeys.POTION_SELECTOR)) {
						needsPotionContents = true;
					}
				}
			}
			return new Order(Map.copyOf(ranks), needsPotionContents);
		} catch (IOException | RuntimeException e) {
			LOGGER.error("Failed to parse JEI custom ingredient order file: {}", path, e);
			return EMPTY;
		}
	}

	private static void writeTemplate(Path path) {
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, "[\n]\n");
			LOGGER.info("Created empty JEI custom ingredient order file: {}", path);
		} catch (IOException e) {
			LOGGER.error("Failed to create JEI custom ingredient order template: {}", path, e);
		}
	}

	/** Immutable map of key -> position, plus rank lookup over an ingredient's candidate keys. */
	public static final class Order {
		private final Map<String, Integer> ranks;
		private final boolean needsPotionContents;

		private Order(Map<String, Integer> ranks, boolean needsPotionContents) {
			this.ranks = ranks;
			this.needsPotionContents = needsPotionContents;
		}

		public boolean isEmpty() {
			return ranks.isEmpty();
		}

		/** True if any key needs the (slightly more expensive) potion component to be read. */
		public boolean needsPotionContents() {
			return needsPotionContents;
		}

		/**
		 * @param candidateKeys keys describing the ingredient, e.g. its potion-variant key and
		 *                      its plain registry id
		 * @return the smallest (earliest) rank among the candidate keys, or {@link #UNRANKED}
		 */
		public int rank(List<String> candidateKeys) {
			int best = UNRANKED;
			for (String key : candidateKeys) {
				Integer idx = ranks.get(key);
				if (idx != null && idx < best) {
					best = idx;
				}
			}
			return best;
		}
	}
}
