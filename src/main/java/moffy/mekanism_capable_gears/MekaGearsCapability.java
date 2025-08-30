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

public abstract class MekaGearsCapability implements IMekaGears {
    public static final Capability<IMekaGears> MEKA_GEARS_CAPABILITY = CapabilityManager.get(new CapabilityToken<IMekaGears>() {
    });

    public abstract static class Provider implements ICapabilityProvider {

        protected final IMekaGears mekaGearsCapability;
        private final ItemCapabilityWrapper itemCapabilityWrapper;

        public Provider(ItemStack stack){
            this.mekaGearsCapability = getMekaGearsCapability(stack);
            List<ItemCapabilityWrapper.ItemCapability> capabilities = new ArrayList<>();

            if(mekaGearsCapability.areCapabilityConfigsLoaded()){
                mekaGearsCapability.gatherCapabilities(stack, capabilities);
            }

            this.itemCapabilityWrapper = new ItemCapabilityWrapper(stack, capabilities.toArray(ItemCapabilityWrapper.ItemCapability[]::new));
        }

        public abstract IMekaGears getMekaGearsCapability(ItemStack stack);

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
