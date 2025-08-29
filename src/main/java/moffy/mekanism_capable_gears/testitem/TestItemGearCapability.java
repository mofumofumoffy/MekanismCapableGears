package moffy.mekanism_capable_gears.testitem;

import mekanism.api.gear.IModule;
import mekanism.api.math.FloatingLong;
import mekanism.common.config.MekanismConfig;
import mekanism.common.content.gear.shared.ModuleEnergyUnit;
import mekanism.common.registries.MekanismModules;
import moffy.mekanism_capable_gears.MekaGearsCapability;
import moffy.mekanism_capable_gears.MekanismCapableGears;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class TestItemGearCapability extends MekaGearsCapability {
    public TestItemGearCapability(ItemStack stack) {
        super(stack);
    }

    @Override
    public ResourceLocation getRadialId() {
        return ResourceLocation.fromNamespaceAndPath(MekanismCapableGears.MODID, "test_item");
    }

    @Override
    public FloatingLong getChargeRate(ItemStack stack) {
        IModule<ModuleEnergyUnit> module = getModule(stack, MekanismModules.ENERGY_UNIT);
        return module == null ? MekanismConfig.gear.mekaToolBaseChargeRate.get() : module.getCustomInstance().getChargeRate(module);
    }

    @Override
    public FloatingLong getMaxEnergy(ItemStack stack) {
        IModule<ModuleEnergyUnit> module = getModule(stack, MekanismModules.ENERGY_UNIT);
        return module == null ? MekanismConfig.gear.mekaToolBaseEnergyCapacity.get() : module.getCustomInstance().getEnergyCapacity(module);
    }


}
