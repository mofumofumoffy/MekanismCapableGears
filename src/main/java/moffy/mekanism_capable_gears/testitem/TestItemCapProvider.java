package moffy.mekanism_capable_gears.testitem;

import moffy.mekanism_capable_gears.interfaces.IMekaGear;
import moffy.mekanism_capable_gears.MekaGearCapability;
import net.minecraft.world.item.ItemStack;

public class TestItemCapProvider extends MekaGearCapability.Provider {
    public TestItemCapProvider(ItemStack stack) {
        super(stack);
    }

    @Override
    public IMekaGear getMekaGearsCapability(ItemStack stack) {
        return new TestItemGearCapability();
    }
}
