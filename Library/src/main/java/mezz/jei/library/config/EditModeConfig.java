package mezz.jei.library.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mezz.jei.api.helpers.ICodecHelper;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.runtime.IEditModeConfig;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.codecs.EnumCodec;
import mezz.jei.common.config.file.JsonArrayFileHelper;
import mezz.jei.common.ingredients.TypedIngredientUtil;
import mezz.jei.library.ingredients.IngredientVisibility;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryOps;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public class EditModeConfig implements IEditModeConfig {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final int VERSION = 2;

	private final Map<Object, Pair<HideMode, ITypedIngredient<?>>> blacklist = new LinkedHashMap<>();
	private final Map<Object, ITypedIngredient<?>> strictBlacklist = new LinkedHashMap<>();
	private final ISerializer serializer;
	private final IIngredientManager ingredientManager;
	private WeakReference<IngredientVisibility> ingredientVisibilityRef = new WeakReference<>(null);

	public EditModeConfig(ISerializer serializer, IIngredientManager ingredientManager) {
		this.ingredientManager = ingredientManager;
		this.serializer = serializer;
		this.serializer.initialize(this);
		this.serializer.load(this);
	}

	public <V> void addIngredientToConfigBlacklist(ITypedIngredient<V> typedIngredient, HideMode blacklistType, IIngredientHelper<V> ingredientHelper) {
		if (addIngredientToConfigBlacklistInternal(typedIngredient, blacklistType, ingredientHelper)) {
			serializer.save(this);
			notifyListenersOfVisibilityChange(typedIngredient, false);
		}
	}

	private <V> void addIngredientToConfigBlacklistInternal(
		ITypedIngredient<V> typedIngredient,
		HideMode blacklistType
	) {
		IIngredientHelper<V> ingredientHelper = ingredientManager.getIngredientHelper(typedIngredient.getType());
		addIngredientToConfigBlacklistInternal(typedIngredient, blacklistType, ingredientHelper);
	}

	private <V> boolean addIngredientToConfigBlacklistInternal(
		ITypedIngredient<V> typedIngredient,
		HideMode blacklistType,
		IIngredientHelper<V> ingredientHelper
	) {
		if (blacklistType == HideMode.STRICT) {
			Optional<Object> strictKey = serializer.computeStrictKey(typedIngredient);
			if (strictKey.isEmpty()) {
				LOGGER.warn("Could not compute a strict blacklist key for ingredient: {}", ingredientHelper.getErrorInfo(typedIngredient.getIngredient()));
				return false;
			}
			return strictBlacklist.put(strictKey.get(), typedIngredient) == null;
		}

		Object wildcardUid = getIngredientUid(typedIngredient, HideMode.WILDCARD, ingredientHelper);
		Object uid = getIngredientUid(typedIngredient, HideMode.SINGLE, ingredientHelper);
		if (wildcardUid.equals(uid)) {
			// there's only one type of this ingredient, adding it as SINGLE is the same as adding it as WILDCARD.
			blacklistType = HideMode.WILDCARD;
		}

		if (blacklistType == HideMode.SINGLE) {
			return blacklist.put(uid, new Pair<>(blacklistType, typedIngredient)) == null;
		} else if (blacklistType == HideMode.WILDCARD) {
			return blacklist.put(wildcardUid, new Pair<>(blacklistType, typedIngredient)) == null;
		}

		return false;
	}

	public <V> boolean isIngredientOnConfigBlacklist(ITypedIngredient<V> typedIngredient, IIngredientHelper<V> ingredientHelper) {
		if (isIngredientOnStrictBlacklist(typedIngredient)) {
			return true;
		}
		for (HideMode hideMode : HideMode.values()) {
			if (hideMode == HideMode.STRICT) {
				continue;
			}
			if (isIngredientOnConfigBlacklist(typedIngredient, hideMode, ingredientHelper)) {
				return true;
			}
		}
		return false;
	}

	private <V> boolean isIngredientOnStrictBlacklist(ITypedIngredient<V> typedIngredient) {
		if (strictBlacklist.isEmpty()) {
			return false;
		}
		return serializer.computeStrictKey(typedIngredient)
			.map(strictBlacklist::containsKey)
			.orElse(false);
	}

	private <V> Set<HideMode> getIngredientOnConfigBlacklist(ITypedIngredient<V> ingredient, IIngredientHelper<V> ingredientHelper) {
		final Object singleUid = getIngredientUid(ingredient, HideMode.SINGLE, ingredientHelper);
		final Object wildcardUid = getIngredientUid(ingredient, HideMode.WILDCARD, ingredientHelper);

		Set<HideMode> set = new HashSet<>();
		if (isIngredientOnStrictBlacklist(ingredient)) {
			set.add(HideMode.STRICT);
		}
		if (singleUid.equals(wildcardUid)) {
			if (blacklist.containsKey(singleUid)) {
				// there's only one type of this ingredient, adding it as SINGLE is the same as adding it as WILDCARD.
				set.add(HideMode.SINGLE);
				set.add(HideMode.WILDCARD);
			}
			return Collections.unmodifiableSet(set);
		}

		if (blacklist.containsKey(singleUid)) {
			set.add(HideMode.SINGLE);
		}
		if (blacklist.containsKey(wildcardUid)) {
			set.add(HideMode.WILDCARD);
		}
		return Collections.unmodifiableSet(set);
	}

	public <V> boolean isIngredientOnConfigBlacklist(ITypedIngredient<V> typedIngredient, HideMode blacklistType, IIngredientHelper<V> ingredientHelper) {
		if (blacklistType == HideMode.STRICT) {
			return isIngredientOnStrictBlacklist(typedIngredient);
		}
		final Object uid = getIngredientUid(typedIngredient, blacklistType, ingredientHelper);
		return blacklist.containsKey(uid);
	}

	private static <V> Object getIngredientUid(ITypedIngredient<V> typedIngredient, HideMode blacklistType, IIngredientHelper<V> ingredientHelper) {
		return switch (blacklistType) {
			case SINGLE -> ingredientHelper.getUid(typedIngredient, UidContext.Ingredient);
			case WILDCARD -> ingredientHelper.getGroupingUid(typedIngredient);
			case STRICT -> throw new IllegalArgumentException("STRICT hide mode does not use getUid; use computeStrictKey instead");
		};
	}

	@Override
	public <V> boolean isIngredientHiddenUsingConfigFile(ITypedIngredient<V> ingredient) {
		ITypedIngredient<V> checkedIngredient = TypedIngredientUtil.checkTypedIngredientFromApi(ingredientManager, ingredient);
		IIngredientType<V> type = checkedIngredient.getType();
		IIngredientHelper<V> ingredientHelper = ingredientManager.getIngredientHelper(type);
		return isIngredientOnConfigBlacklist(checkedIngredient, ingredientHelper);
	}

	@Override
	public <V> Set<HideMode> getIngredientHiddenUsingConfigFile(ITypedIngredient<V> ingredient) {
		ITypedIngredient<V> checkedIngredient = TypedIngredientUtil.checkTypedIngredientFromApi(ingredientManager, ingredient);
		IIngredientType<V> type = checkedIngredient.getType();
		IIngredientHelper<V> ingredientHelper = ingredientManager.getIngredientHelper(type);
		return getIngredientOnConfigBlacklist(checkedIngredient, ingredientHelper);
	}

	@Override
	public <V> void hideIngredientUsingConfigFile(ITypedIngredient<V> ingredient, HideMode hideMode) {
		ITypedIngredient<V> checkedIngredient = TypedIngredientUtil.checkTypedIngredientFromApi(ingredientManager, ingredient);
		IIngredientType<V> type = checkedIngredient.getType();
		IIngredientHelper<V> ingredientHelper = ingredientManager.getIngredientHelper(type);
		addIngredientToConfigBlacklist(checkedIngredient, hideMode, ingredientHelper);
	}

	@Override
	public <V> void showIngredientUsingConfigFile(ITypedIngredient<V> ingredient, HideMode hideMode) {
		ITypedIngredient<V> checkedIngredient = TypedIngredientUtil.checkTypedIngredientFromApi(ingredientManager, ingredient);
		IIngredientType<V> type = checkedIngredient.getType();
		IIngredientHelper<V> ingredientHelper = ingredientManager.getIngredientHelper(type);

		boolean removed;
		if (hideMode == HideMode.STRICT) {
			removed = serializer.computeStrictKey(checkedIngredient)
				.map(strictBlacklist::remove)
				.isPresent();
		} else {
			final Object blacklistUid = getIngredientUid(checkedIngredient, hideMode, ingredientHelper);
			removed = blacklist.remove(blacklistUid) != null;
		}

		if (removed) {
			serializer.save(this);
			notifyListenersOfVisibilityChange(checkedIngredient, true);
		}
	}

	public void registerListener(IngredientVisibility ingredientVisibility) {
		this.ingredientVisibilityRef = new WeakReference<>(ingredientVisibility);
	}

	public interface ISerializer {
		void initialize(EditModeConfig config);
		void save(EditModeConfig config);
		void load(EditModeConfig config);

		/**
		 * Computes an opaque key for {@link HideMode#STRICT} matching, based on the ingredient's full
		 * encoded representation. Two ingredients with the same strict key are considered identical for
		 * {@link HideMode#STRICT} purposes; returns {@link Optional#empty()} if the ingredient can't be encoded.
		 */
		Optional<Object> computeStrictKey(ITypedIngredient<?> ingredient);
	}

	public static class FileSerializer implements ISerializer {
		private final Path path;
		private final Codec<ITypedIngredient<?>> typedIngredientCodec;
		private final Codec<Pair<HideMode, ITypedIngredient<?>>> codec;
		private final RegistryOps<JsonElement> registryOps;

		public FileSerializer(Path path, RegistryAccess registryAccess, ICodecHelper codecHelper) {
			this.path = path;
			this.typedIngredientCodec = codecHelper.getTypedIngredientCodec().codec();
			this.codec = RecordCodecBuilder.create(builder -> {
				return builder.group(
						EnumCodec.create(HideMode.class)
							.fieldOf("hide_mode")
							.forGetter(Pair::getFirst),
						typedIngredientCodec
							.fieldOf("ingredient")
							.forGetter(Pair::getSecond)
					)
					.apply(builder, Pair::new);
			});
			this.registryOps = registryAccess.createSerializationContext(JsonOps.INSTANCE);
		}

		@Override
		public void initialize(EditModeConfig config) {
			if (!Files.exists(path)) {
				save(config);
			}
		}

		@Override
		public void save(EditModeConfig config) {
			try {
				List<Pair<HideMode, ITypedIngredient<?>>> entries = Stream.concat(
					config.blacklist.values().stream(),
					config.strictBlacklist.values().stream()
						.map(ingredient -> new Pair<HideMode, ITypedIngredient<?>>(HideMode.STRICT, ingredient))
				).toList();

				JsonArrayFileHelper.write(
					path,
					VERSION,
					entries,
					codec,
					registryOps,
					error -> {
						LOGGER.error("Encountered an error when saving the blacklist config to file {}\n{}", path, error);
					},
					(element, exception) -> {
						LOGGER.error("Encountered an exception when saving the blacklist config to file {}\n{}", path, element, exception);
					}
				);
				LOGGER.debug("Saved blacklist config to file: {}", path);
			} catch (RuntimeException | IOException e) {
				LOGGER.error("Failed to save blacklist config to file {}", path, e);
			}
		}

		@Override
		public Optional<Object> computeStrictKey(ITypedIngredient<?> ingredient) {
			return typedIngredientCodec.encodeStart(registryOps, ingredient)
				.result()
				.map(jsonElement -> (Object) jsonElement);
		}

		@Override
		public void load(EditModeConfig config) {
			if (!Files.exists(path)) {
				return;
			}
			List<Pair<HideMode, ITypedIngredient<?>>> results;
			try (BufferedReader reader = Files.newBufferedReader(path)) {
				results = JsonArrayFileHelper.read(
					reader,
					VERSION,
					codec,
					registryOps,
					(element, error) -> {
						LOGGER.error("Encountered an error when loading the blacklist config from file {}\n{}\n{}", path, element, error);
					},
					(element, exception) -> {
						LOGGER.error("Encountered an exception when loading the blacklist config from file {}\n{}", path, element, exception);
					}
				);
			} catch (JsonIOException | JsonSyntaxException | IOException | IllegalArgumentException e) {
				LOGGER.error("Failed to load blacklist from file {}", path, e);
				results = List.of();
			}

			for (Pair<HideMode, ITypedIngredient<?>> pair : results) {
				config.addIngredientToConfigBlacklistInternal(pair.getSecond(), pair.getFirst());
			}
		}
	}

	private <T> void notifyListenersOfVisibilityChange(ITypedIngredient<T> ingredient, boolean visible) {
		IngredientVisibility ingredientVisibility = this.ingredientVisibilityRef.get();
		if (ingredientVisibility != null) {
			ingredientVisibility.notifyListeners(List.of(ingredient), visible);
		}
	}
}
