package moffy.mekanism_capable_gears.interfaces;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

public interface IFluidTankItem {
    FluidStack getContainedFluid(ItemStack stack, FluidStack type);
}
