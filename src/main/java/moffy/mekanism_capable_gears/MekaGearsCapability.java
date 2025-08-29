package moffy.mekanism_capable_gears;

import mekanism.api.math.FloatingLong;
import mekanism.api.radial.RadialData;
import mekanism.api.radial.mode.IRadialMode;
import mekanism.api.radial.mode.NestedRadialMode;
import mekanism.common.capabilities.ItemCapabilityWrapper;
import mekanism.common.capabilities.energy.BasicEnergyContainer;
import mekanism.common.capabilities.energy.item.RateLimitEnergyHandler;
import mekanism.common.config.MekanismConfig;
import mekanism.common.content.gear.IBlastingItem;
import mekanism.common.content.gear.IModuleContainerItem;
import mekanism.common.content.gear.Module;
import mekanism.common.lib.radial.IGenericRadialModeItem;
import mekanism.common.lib.radial.data.NestingRadialData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public abstract class MekaGearsCapability implements IModuleContainerItem, IGenericRadialModeItem {
    public static final Capability<MekaGearsCapability> MEKA_GEARS_CAPABILITY = CapabilityManager.get(new CapabilityToken<MekaGearsCapability>() {
    });

    protected final ItemStack stack;

    public MekaGearsCapability(ItemStack stack) {
        this.stack = stack;
    }

    public boolean areCapabilityConfigsLoaded() {
        return MekanismConfig.gear.isLoaded();
    }

    public void gatherCapabilities(List<ItemCapabilityWrapper.ItemCapability> capabilities) {
        capabilities.add(RateLimitEnergyHandler.create(() -> getChargeRate(stack), () -> getMaxEnergy(stack), BasicEnergyContainer.manualOnly,
                BasicEnergyContainer.alwaysTrue));
    }

    @Override
    public @Nullable RadialData<?> getRadialData(ItemStack stack) {
        List<NestedRadialMode> nestedModes = new ArrayList<>();
        Consumer<NestedRadialMode> adder = nestedModes::add;
        for (Module<?> module : getModules(stack)) {
            if (module.handlesRadialModeChange()) {
                module.addRadialModes(stack, adder);
            }
        }
        if (nestedModes.isEmpty()) {
            return null;
        } else if (nestedModes.size() == 1) {
            return nestedModes.get(0).nestedData();
        }
        return new NestingRadialData(getRadialId(), nestedModes);
    }

    @Override
    public <M extends IRadialMode> @Nullable M getMode(ItemStack stack, RadialData<M> radialData) {
        for (Module<?> module : getModules(stack)) {
            if (module.handlesRadialModeChange()) {
                M mode = module.getMode(stack, radialData);
                if (mode != null) {
                    return mode;
                }
            }
        }
        return null;
    }

    @Override
    public <M extends IRadialMode> void setMode(ItemStack stack, Player player, RadialData<M> radialData, M mode) {
        for (Module<?> module : getModules(stack)) {
            if (module.handlesRadialModeChange() && module.setMode(player, stack, radialData, mode)) {
                return;
            }
        }
    }

    @Override
    public void changeMode(@NotNull Player player, @NotNull ItemStack stack, int shift, DisplayChange displayChange) {
        for (Module<?> module : getModules(stack)) {
            if (module.handlesModeChange()) {
                module.changeMode(player, stack, shift, displayChange);
                return;
            }
        }
    }

    public abstract ResourceLocation getRadialId();
    public abstract FloatingLong getChargeRate(ItemStack stack);
    public abstract FloatingLong getMaxEnergy(ItemStack stack);

    public abstract static class Provider implements ICapabilityProvider {

        protected final MekaGearsCapability mekaGearsCapability;
        private final ItemCapabilityWrapper itemCapabilityWrapper;

        public Provider(ItemStack stack){
            this.mekaGearsCapability = getMekaGearsCapability(stack);
            List<ItemCapabilityWrapper.ItemCapability> capabilities = new ArrayList<>();

            if(mekaGearsCapability.areCapabilityConfigsLoaded()){
                mekaGearsCapability.gatherCapabilities(capabilities);
            }

            this.itemCapabilityWrapper = new ItemCapabilityWrapper(stack, capabilities.toArray(ItemCapabilityWrapper.ItemCapability[]::new));
        }

        public abstract MekaGearsCapability getMekaGearsCapability(ItemStack stack);

        @Override
        public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction direction) {
            if(capability == MEKA_GEARS_CAPABILITY){
                return LazyOptional.of(()->this.mekaGearsCapability).cast();
            } else {
                return this.itemCapabilityWrapper.getCapability(capability, direction);
            }
        }
    }
}
