package com.thaumaturgicinfusion.compat;

import com.thaumaturgicinfusion.TIRegistry;
import com.thaumaturgicinfusion.ThaumaturgicInfusion;
import com.thaumaturgicinfusion.effect.AspectEffect;
import com.thaumaturgicinfusion.world.Infusion;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * JEI: an Aspect Effects category saying what each infusable effect does once it is in a block (the sigil the
 * infusion recipe makes, its essentia cost and the original effect text), plus information pages for the sigil, the
 * core and the essentia blocks. The infusion and arcane recipes themselves are Thaumaturge's types, which its plugin
 * shows. Effects come from the synced aspect_effect registry, so datapack effects appear too. Loaded only with JEI.
 */
@JeiPlugin
public final class TIJeiPlugin implements IModPlugin {
    record Effect(Identifier id, AspectEffect effect) {
    }

    static final IRecipeType<Effect> ASPECT_EFFECTS = IRecipeType.create(ThaumaturgicInfusion.id("aspect_effects"), Effect.class);
    // Mirrors ESSENTIA_PER_COST in tools/gen_data.py, which scales each infusion recipe's essentia from the cost.
    private static final int ESSENTIA_PER_COST = 8;
    private static final String[] INFO = {"infusion_sigil", "infusion_core", "essentia_block", "essentia_bricks", "essentia_chiseled"};

    @Override
    public Identifier getPluginUid() {
        return ThaumaturgicInfusion.id("jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new EffectCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(ASPECT_EFFECTS, TIRegistry.INFUSION_CORE_ITEM.get());
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<Effect> effects = new ArrayList<>();
        var level = Minecraft.getInstance().level;
        if (level != null) {
            level.registryAccess().lookup(TIRegistry.ASPECT_EFFECT_KEY).ifPresent(l -> l.listElements()
                    .forEach(h -> effects.add(new Effect(h.key().identifier(), h.value()))));
        }
        effects.sort((a, b) -> a.id().compareTo(b.id()));
        registration.addRecipes(ASPECT_EFFECTS, effects);
        for (String name : INFO) {
            BuiltInRegistries.ITEM.getOptional(ThaumaturgicInfusion.id(name)).ifPresent(item ->
                    registration.addIngredientInfo(item, Component.translatable("jei.thaumaturgicinfusion.info." + name)));
        }
    }

    private static final class EffectCategory extends AbstractRecipeCategory<Effect> {
        EffectCategory(IGuiHelper gui) {
            super(ASPECT_EFFECTS, Component.translatable("jei.thaumaturgicinfusion.aspect_effects"),
                    gui.createDrawableItemLike(TIRegistry.INFUSION_SIGIL.get()), 160, 74);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, Effect recipe, IFocusGroup focuses) {
            ItemStack sigil = new ItemStack(TIRegistry.INFUSION_SIGIL.get());
            sigil.set(TIRegistry.INFUSION.get(), new Infusion(List.of(recipe.id())));
            builder.addOutputSlot(1, 1).setOutputSlotBackground().add(sigil);
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, Effect recipe, IFocusGroup focuses) {
            Identifier id = recipe.id();
            builder.addText(Component.translatable("aspect_effect." + id.getNamespace() + "." + id.getPath()), 136, 10)
                    .setPosition(24, 2).setColor(0xFF404040);
            builder.addText(Component.translatable("jei.thaumaturgicinfusion.cost", recipe.effect().cost() * ESSENTIA_PER_COST), 136, 10)
                    .setPosition(24, 12).setColor(0xFF707070);
            builder.addText(Component.translatableWithFallback("jei." + id.getNamespace() + ".effect." + id.getPath(), ""), 160, 50)
                    .setPosition(0, 24).setColor(0xFF404040);
        }
    }
}
