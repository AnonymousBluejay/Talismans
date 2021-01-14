package com.willfp.talismans.talismans;


import com.willfp.eco.util.StringUtils;
import com.willfp.eco.util.config.Configs;
import com.willfp.eco.util.optional.Prerequisite;
import com.willfp.eco.util.plugin.AbstractEcoPlugin;
import com.willfp.eco.util.recipes.EcoShapedRecipe;
import com.willfp.eco.util.recipes.parts.ComplexRecipePart;
import com.willfp.eco.util.recipes.parts.SimpleRecipePart;
import com.willfp.talismans.config.TalismansConfigs;
import com.willfp.talismans.config.configs.TalismanConfig;
import com.willfp.talismans.display.TalismanDisplay;
import com.willfp.talismans.talismans.meta.TalismanStrength;
import com.willfp.talismans.talismans.util.TalismanChecks;
import com.willfp.talismans.talismans.util.TalismanUtils;
import com.willfp.talismans.talismans.util.Watcher;
import lombok.AccessLevel;
import lombok.Getter;
import org.apache.commons.lang.Validate;
import org.apache.commons.lang.WordUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@SuppressWarnings({"unchecked", "deprecation", "RedundantSuppression"})
public abstract class Talisman implements Listener, Watcher {
    /**
     * Instance of Talismans for talismans to be able to access.
     */
    @Getter(AccessLevel.PROTECTED)
    private final AbstractEcoPlugin plugin = AbstractEcoPlugin.getInstance();

    /**
     * The key to store talismans in meta.
     */
    @Getter
    private final NamespacedKey key;

    /**
     * The display name of the talisman.
     */
    @Getter
    private String name;

    /**
     * The description of the talisman.
     */
    @Getter
    private String description;

    /**
     * Get description of talisman formatted.
     */
    @Getter
    private List<String> formattedDescription;

    /**
     * The config name of the talisman.
     */
    @Getter
    private final String configName;

    /**
     * The strength of the talisman
     * <p>
     * Talisman is weakest, then ring, then relic.
     */
    @Getter
    private final TalismanStrength strength;

    /**
     * The talisman's config.
     */
    @Getter
    private final TalismanConfig config;

    /**
     * The talisman item.
     */
    @Getter
    private ItemStack itemStack;

    /**
     * The talisman recipe.
     */
    @Getter
    private EcoShapedRecipe recipe = null;

    /**
     * The base64 skull texture.
     */
    @Getter
    private String skullBase64;

    /**
     * The names of the worlds that this talisman is disabled in.
     */
    @Getter
    private final Set<String> disabledWorldNames = new HashSet<>();

    /**
     * The worlds that this talisman is disabled in.
     */
    @Getter
    private final List<World> disabledWorlds = new ArrayList<>();

    /**
     * If the talisman is enabled.
     */
    @Getter
    private boolean enabled;

    /**
     * Create a new Talisman.
     *
     * @param key           The key name of the talisman.
     * @param strength      The strength of the talisman.
     * @param prerequisites Optional {@link Prerequisite}s that must be met.
     */
    protected Talisman(@NotNull final String key,
                       @NotNull final TalismanStrength strength,
                       @NotNull final Prerequisite... prerequisites) {
        this.strength = strength;
        this.key = this.getPlugin().getNamespacedKeyFactory().create(key);
        this.configName = key.replace("_", "");
        TalismansConfigs.addTalismanConfig(new TalismanConfig(this.configName, this.strength, this.getClass()));
        this.config = TalismansConfigs.getTalismanConfig(this.configName);

        if (Bukkit.getPluginManager().getPermission("talismans.fromtable." + configName) == null) {
            Permission permission = new Permission(
                    "talismans.fromtable." + configName,
                    "Allows getting " + configName + " from a Crafting Table",
                    PermissionDefault.TRUE
            );
            permission.addParent(Objects.requireNonNull(Bukkit.getPluginManager().getPermission("talismans.fromtable.*")), true);
            Bukkit.getPluginManager().addPermission(permission);
        }

        if (!Prerequisite.areMet(prerequisites)) {
            return;
        }

        Talismans.addNewTalisman(this);
        this.update();
    }

    /**
     * Update the talisman based off config values.
     * This can be overridden but may lead to unexpected behavior.
     */
    public void update() {
        name = StringUtils.translate(config.getString("name"));
        description = StringUtils.translate(config.getString("description"));
        skullBase64 = config.getString(Talismans.GENERAL_LOCATION + "texture");
        disabledWorldNames.clear();
        disabledWorldNames.addAll(config.getStrings(Talismans.GENERAL_LOCATION + "disabled-in-worlds"));
        disabledWorlds.clear();

        formattedDescription = Arrays.stream(WordUtils.wrap(description, Configs.CONFIG.getInt("description.wrap"), "\n", false).split("\\r?\\n"))
                .map(s -> TalismanDisplay.PREFIX + StringUtils.translate(Configs.LANG.getString("description-color") + s)).collect(Collectors.toList());

        List<String> worldNames = Bukkit.getWorlds().stream().map(World::getName).map(String::toLowerCase).collect(Collectors.toList());
        List<String> disabledExistingWorldNames = disabledWorldNames.stream().filter(s -> worldNames.contains(s.toLowerCase())).collect(Collectors.toList());
        disabledWorlds.addAll(Bukkit.getWorlds().stream().filter(world -> disabledExistingWorldNames.contains(world.getName().toLowerCase())).collect(Collectors.toList()));
        enabled = config.getBool("enabled");

        TalismanUtils.registerPlaceholders(this);

        ItemStack out = new ItemStack(Material.PLAYER_HEAD, 1);
        ItemMeta outMeta = out.getItemMeta();
        assert outMeta != null;
        PersistentDataContainer container = outMeta.getPersistentDataContainer();
        container.set(this.getKey(), PersistentDataType.INTEGER, 1);
        out.setItemMeta(outMeta);
        TalismanDisplay.displayTalisman(out);

        this.itemStack = out;

        if (this.isEnabled()) {
            EcoShapedRecipe.Builder builder = EcoShapedRecipe.builder(this.getPlugin(), this.getKey().getKey())
                    .setOutput(out);

            List<String> recipeStrings = this.getConfig().getStrings(Talismans.OBTAINING_LOCATION + "recipe");

            for (int i = 0; i < 9; i++) {
                if (recipeStrings.get(i).startsWith("talisman:")) {
                    String talismanKey = recipeStrings.get(i).split(":")[1];
                    NamespacedKey talismanNamespacedKey = new NamespacedKey(this.getPlugin(), talismanKey);
                    Talisman talisman = Talismans.getByKey(talismanNamespacedKey);
                    Validate.notNull(talisman, "Talisman specified in " + this.getConfigName() + ".yml has an invalid recipe!");
                    builder.setRecipePart(i, new ComplexRecipePart(test -> Objects.equals(talisman, TalismanChecks.getTalismanOnItem(test)), new ItemStack(Material.PLAYER_HEAD)));
                } else {
                    builder.setRecipePart(i, new SimpleRecipePart(Material.valueOf(recipeStrings.get(i).toUpperCase())));
                }
            }

            this.recipe = builder.build();
            this.recipe.register();
        }

        postUpdate();
    }

    protected void postUpdate() {
        // Unused as some talismans may have postUpdate tasks, however most won't.
    }

    /**
     * Get the name of the talisman, formatted with color.
     *
     * @return The name.
     */
    public String getFormattedName() {
        return this.getStrength().getColor() + this.getName();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof Talisman)) {
            return false;
        }

        Talisman talisman = (Talisman) o;
        return this.getKey().equals(talisman.getKey());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getKey());
    }

    @Override
    public String toString() {
        return "Talisman{"
                + this.getKey()
                + "}";
    }
}
