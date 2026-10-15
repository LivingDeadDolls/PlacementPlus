package placementplus.config;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.ListOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.ItemControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import placementplus.Constants;
import placementplus.ItemListMode;
import placementplus.PlacementRestriction;
import placementplus.platform.Services;

/** Settings saved to config/placementplus.json5. */
public final class PlacementPlusConfig {
    public static final ConfigClassHandler<PlacementPlusConfig> HANDLER = ConfigClassHandler.createBuilder(PlacementPlusConfig.class)
            .id(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "config"))
            .serializer(config -> GsonConfigSerializerBuilder.create(config)
                    .setPath(Services.PLATFORM.getConfigDir().resolve(Constants.MOD_ID + ".json5"))
                    .setJson5(true)
                    .build())
            .build();

    private static final int MAX_FAST_PLACEMENT_COUNT = 16;
    private static final int MAX_LAYER_HEIGHT = 1000;
    private static final int MAX_GRID_SPACING = 1000;
    private static final int MAX_SCAFFOLD_DISTANCE = 20;

    @SerialEntry
    public boolean fastPlacement = true;

    @SerialEntry
    public boolean continuousPlacement = false;

    /** Continuous Placement: the most blocks placed per tick. */
    @SerialEntry
    public int fastPlacementCount = 2;

    @SerialEntry
    public boolean keepFirstOrientation = false;

    @SerialEntry
    public PlacementRestriction placementRestriction = PlacementRestriction.NONE;

    /** Layer restriction: how many layers up from the first block (negative: down), counting it. */
    @SerialEntry
    public int restrictionLayerHeight = 1;

    /** Only every this many blocks from the first block; 1 for every block. */
    @SerialEntry
    public int placementGrid = 1;

    @SerialEntry
    public ItemListMode fastPlacementItems = ItemListMode.EXCLUDE;

    /** Item ids, such as "minecraft:ender_chest". */
    @SerialEntry
    public List<String> fastPlacementExcluded = new ArrayList<>(List.of("minecraft:ender_chest", "minecraft:shulker_box"));

    @SerialEntry
    public List<String> fastPlacementIncluded = new ArrayList<>();

    @SerialEntry
    public boolean flexiblePlacement = true;

    @SerialEntry
    public boolean fakeSneak = true;

    @SerialEntry
    public boolean bridging = true;

    @SerialEntry
    public boolean scaffoldPlacement = false;

    /** Scaffold Placement: how far along a run of the same block it looks for a free space. */
    @SerialEntry
    public int scaffoldDistance = 5;

    @SerialEntry
    public boolean placementPreview = true;

    private static boolean loaded;

    public static PlacementPlusConfig get() {
        if (!loaded) {
            loaded = true;
            HANDLER.load();
            if (HANDLER.instance().repair()) {
                HANDLER.save();
            }
        }
        return HANDLER.instance();
    }

    public static void save() {
        HANDLER.save();
    }

    /**
     * Puts back values a hand-edited or older file got wrong: unknown enum names are read as null, and numbers
     * may be out of range. Returns whether anything changed.
     */
    private boolean repair() {
        PlacementPlusConfig defaults = new PlacementPlusConfig();
        boolean changed = false;
        if (placementRestriction == null) {
            placementRestriction = defaults.placementRestriction;
            changed = true;
        }
        if (fastPlacementItems == null) {
            fastPlacementItems = defaults.fastPlacementItems;
            changed = true;
        }
        if (fastPlacementExcluded == null) {
            fastPlacementExcluded = defaults.fastPlacementExcluded;
            changed = true;
        }
        if (fastPlacementIncluded == null) {
            fastPlacementIncluded = defaults.fastPlacementIncluded;
            changed = true;
        }
        int count = Math.clamp(fastPlacementCount, 1, MAX_FAST_PLACEMENT_COUNT);
        int height = Math.clamp(restrictionLayerHeight, -MAX_LAYER_HEIGHT, MAX_LAYER_HEIGHT);
        int grid = Math.clamp(placementGrid, 1, MAX_GRID_SPACING);
        int scaffold = Math.clamp(scaffoldDistance, 1, MAX_SCAFFOLD_DISTANCE);
        if (count != fastPlacementCount || height != restrictionLayerHeight || grid != placementGrid || scaffold != scaffoldDistance) {
            fastPlacementCount = count;
            restrictionLayerHeight = height;
            placementGrid = grid;
            scaffoldDistance = scaffold;
            changed = true;
        }
        return changed;
    }

    /** Whether Fast Placement may be used with this item, by the item lists. */
    public boolean allowsFastPlacement(Item item) {
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        return switch (fastPlacementItems) {
            case ALL -> true;
            case EXCLUDE -> !fastPlacementExcluded.contains(id);
            case INCLUDE -> fastPlacementIncluded.contains(id);
        };
    }

    public static Screen createScreen(Screen parent) {
        return YetAnotherConfigLib.create(HANDLER, (defaults, config, builder) -> builder
                .title(Component.translatable("placementplus.config.title"))
                .category(ConfigCategory.createBuilder()
                        .name(Component.translatable("placementplus.config.category.general"))
                        .option(toggle("fast_placement", defaults, config, c -> c.fastPlacement, (c, v) -> c.fastPlacement = v))
                        .option(toggle("flexible_placement", defaults, config, c -> c.flexiblePlacement, (c, v) -> c.flexiblePlacement = v))
                        .option(toggle("fake_sneak", defaults, config, c -> c.fakeSneak, (c, v) -> c.fakeSneak = v))
                        .option(toggle("bridging", defaults, config, c -> c.bridging, (c, v) -> c.bridging = v))
                        .option(toggle("scaffold_placement", defaults, config, c -> c.scaffoldPlacement, (c, v) -> c.scaffoldPlacement = v))
                        .option(slider("scaffold_distance", 1, MAX_SCAFFOLD_DISTANCE, defaults.scaffoldDistance,
                                () -> config.scaffoldDistance, value -> config.scaffoldDistance = value))
                        .option(toggle("placement_preview", defaults, config, c -> c.placementPreview, (c, v) -> c.placementPreview = v))
                        .build())
                .category(ConfigCategory.createBuilder()
                        .name(Component.translatable("placementplus.config.category.fast_placement"))
                        .option(toggle("continuous_placement", defaults, config, c -> c.continuousPlacement, (c, v) -> c.continuousPlacement = v))
                        .option(slider("fast_placement_count", 1, MAX_FAST_PLACEMENT_COUNT, defaults.fastPlacementCount,
                                () -> config.fastPlacementCount, value -> config.fastPlacementCount = value))
                        .option(toggle("keep_first_orientation", defaults, config, c -> c.keepFirstOrientation, (c, v) -> c.keepFirstOrientation = v))
                        .option(choice("placement_restriction", PlacementRestriction.class, defaults.placementRestriction,
                                () -> config.placementRestriction, value -> config.placementRestriction = value))
                        .option(field("restriction_layer_height", -MAX_LAYER_HEIGHT, MAX_LAYER_HEIGHT, defaults.restrictionLayerHeight,
                                () -> config.restrictionLayerHeight, value -> config.restrictionLayerHeight = value))
                        .option(field("placement_grid", 1, MAX_GRID_SPACING, defaults.placementGrid,
                                () -> config.placementGrid, value -> config.placementGrid = value))
                        .option(choice("fast_placement_items", ItemListMode.class, defaults.fastPlacementItems,
                                () -> config.fastPlacementItems, value -> config.fastPlacementItems = value))
                        .group(itemList("fast_placement_excluded", defaults.fastPlacementExcluded,
                                () -> config.fastPlacementExcluded, value -> config.fastPlacementExcluded = value))
                        .group(itemList("fast_placement_included", defaults.fastPlacementIncluded,
                                () -> config.fastPlacementIncluded, value -> config.fastPlacementIncluded = value))
                        .build()))
                .generateScreen(parent);
    }

    private static Option<Boolean> toggle(String id, PlacementPlusConfig defaults, PlacementPlusConfig config,
                                          Predicate<PlacementPlusConfig> getter, BiConsumer<PlacementPlusConfig, Boolean> setter) {
        return Option.<Boolean>createBuilder()
                .name(Component.translatable("placementplus.config." + id))
                .description(description(id))
                .binding(getter.test(defaults), () -> getter.test(config), value -> setter.accept(config, value))
                .controller(TickBoxControllerBuilder::create)
                .build();
    }

    private static <E extends Enum<E>> Option<E> choice(String id, Class<E> type, E defaultValue, Supplier<E> getter, Consumer<E> setter) {
        String key = "placementplus.config." + id;
        return Option.<E>createBuilder()
                .name(Component.translatable(key))
                .description(description(id))
                .binding(defaultValue, getter, setter)
                .controller(option -> EnumControllerBuilder.create(option)
                        .enumClass(type)
                        .formatValue(value -> Component.translatable(key + "." + value.name().toLowerCase(Locale.ROOT))))
                .build();
    }

    private static Option<Integer> slider(String id, int min, int max, int defaultValue, Supplier<Integer> getter, Consumer<Integer> setter) {
        return Option.<Integer>createBuilder()
                .name(Component.translatable("placementplus.config." + id))
                .description(description(id))
                .binding(defaultValue, getter, setter)
                .controller(option -> IntegerSliderControllerBuilder.create(option).range(min, max).step(1))
                .build();
    }

    private static Option<Integer> field(String id, int min, int max, int defaultValue, Supplier<Integer> getter, Consumer<Integer> setter) {
        return Option.<Integer>createBuilder()
                .name(Component.translatable("placementplus.config." + id))
                .description(description(id))
                .binding(defaultValue, getter, setter)
                .controller(option -> IntegerFieldControllerBuilder.create(option).range(min, max))
                .build();
    }

    /**
     * A list of items, edited with the item picker and saved as ids. Ids of items that do not exist now (from a
     * mod not installed at the moment) cannot be shown, but are kept when the list is saved.
     */
    private static ListOption<Item> itemList(String id, List<String> defaultValue, Supplier<List<String>> getter, Consumer<List<String>> setter) {
        Function<List<String>, List<Item>> toItems = ids -> {
            List<Item> items = new ArrayList<>();
            for (String itemId : ids) {
                item(itemId).ifPresent(items::add);
            }
            return items;
        };
        return ListOption.<Item>createBuilder()
                .name(Component.translatable("placementplus.config." + id))
                .description(description(id))
                .binding(toItems.apply(defaultValue), () -> toItems.apply(getter.get()), items -> {
                    List<String> ids = new ArrayList<>();
                    for (String itemId : getter.get()) {
                        if (item(itemId).isEmpty()) {
                            ids.add(itemId);
                        }
                    }
                    for (Item item : items) {
                        ids.add(BuiltInRegistries.ITEM.getKey(item).toString());
                    }
                    setter.accept(ids);
                })
                .controller(ItemControllerBuilder::create)
                .initial(Items.STONE)
                .collapsed(true)
                .build();
    }

    private static Optional<Item> item(String itemId) {
        Identifier key = Identifier.tryParse(itemId);
        return key == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(key);
    }

    private static OptionDescription description(String id) {
        return OptionDescription.of(Component.translatable("placementplus.config." + id + ".desc"));
    }
}
