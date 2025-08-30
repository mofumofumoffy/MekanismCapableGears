package moffy.mekanism_capable_gears.testitem;

import moffy.mekanism_capable_gears.IMekaGears;
import moffy.mekanism_capable_gears.MekaGearsCapability;
import net.minecraft.world.item.ItemStack;

public class TestItemCapProvider extends MekaGearsCapability.Provider {
    public TestItemCapProvider(ItemStack stack) {
        super(stack);
    }

    @Override
    public IMekaGears getMekaGearsCapability(ItemStack stack) {
        return new TestItemGearCapability();
    }
}
