package mezz.jei.library.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import mezz.jei.api.helpers.ICodecHelper;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IPinnedIngredientOrderConfig;
import mezz.jei.common.config.file.JsonArrayFileHelper;
import mezz.jei.common.ingredients.TypedIngredientUtil;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryOps;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PinnedIngredientOrderConfig implements IPinnedIngredientOrderConfig {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final int VERSION = 1;

	private final List<ITypedIngredient<?>> pinnedOrder = new ArrayList<>();
	private final Map<Object, Integer> pinnedIndexes = new HashMap<>();
	private final IIngredientManager ingredientManager;

	public PinnedIngredientOrderConfig(ISerializer serializer, IIngredientManager ingredientManager) {
		this.ingredientManager = ingredientManager;
		serializer.initialize(this);
		serializer.load(this);
	}

	public void setPinnedOrder(List<ITypedIngredient<?>> orderedIngredients) {
		pinnedOrder.clear();
		pinnedOrder.addAll(orderedIngredients);

		pinnedIndexes.clear();
		int index = 0;
		for (ITypedIngredient<?> typedIngredient : pinnedOrder) {
			Object uid = getIngredientUid(typedIngredient);
			pinnedIndexes.putIfAbsent(uid, index);
			index++;
		}
	}

	private <V> Object getIngredientUid(ITypedIngredient<V> typedIngredient) {
		IIngredientHelper<V> ingredientHelper = ingredientManager.getIngredientHelper(typedIngredient.getType());
		return ingredientHelper.getUid(typedIngredient, UidContext.Ingredient);
	}

	@Override
	public <V> int getSortedIndex(ITypedIngredient<V> ingredient) {
		ITypedIngredient<V> checkedIngredient = TypedIngredientUtil.checkTypedIngredientFromApi(ingredientManager, ingredient);
		Object uid = getIngredientUid(checkedIngredient);
		return pinnedIndexes.getOrDefault(uid, Integer.MAX_VALUE);
	}

	public interface ISerializer {
		void initialize(PinnedIngredientOrderConfig config);
		void save(PinnedIngredientOrderConfig config);
		void load(PinnedIngredientOrderConfig config);
	}

	public static class FileSerializer implements ISerializer {
		private final Path path;
		private final Codec<ITypedIngredient<?>> codec;
		private final RegistryOps<JsonElement> registryOps;

		public FileSerializer(Path path, RegistryAccess registryAccess, ICodecHelper codecHelper) {
			this.path = path;
			this.codec = codecHelper.getTypedIngredientCodec().codec();
			this.registryOps = registryAccess.createSerializationContext(JsonOps.INSTANCE);
		}

		@Override
		public void initialize(PinnedIngredientOrderConfig config) {
			if (!Files.exists(path)) {
				save(config);
			}
		}

		@Override
		public void save(PinnedIngredientOrderConfig config) {
			try {
				JsonArrayFileHelper.write(
					path,
					VERSION,
					config.pinnedOrder,
					codec,
					registryOps,
					error -> {
						LOGGER.error("Encountered an error when saving the ingredient order config to file {}\n{}", path, error);
					},
					(element, exception) -> {
						LOGGER.error("Encountered an exception when saving the ingredient order config to file {}\n{}", path, element, exception);
					}
				);
				LOGGER.debug("Saved ingredient order config to file: {}", path);
			} catch (RuntimeException | IOException e) {
				LOGGER.error("Failed to save ingredient order config to file {}", path, e);
			}
		}

		@Override
		public void load(PinnedIngredientOrderConfig config) {
			if (!Files.exists(path)) {
				return;
			}
			List<ITypedIngredient<?>> results;
			try (BufferedReader reader = Files.newBufferedReader(path)) {
				results = JsonArrayFileHelper.read(
					reader,
					VERSION,
					codec,
					registryOps,
					(element, error) -> {
						LOGGER.error("Encountered an error when loading the ingredient order config from file {}\n{}\n{}", path, element, error);
					},
					(element, exception) -> {
						LOGGER.error("Encountered an exception when loading the ingredient order config from file {}\n{}", path, element, exception);
					}
				);
			} catch (JsonIOException | JsonSyntaxException | IOException | IllegalArgumentException e) {
				LOGGER.error("Failed to load ingredient order config from file {}", path, e);
				results = List.of();
			}
			config.setPinnedOrder(results);
		}
	}
}
