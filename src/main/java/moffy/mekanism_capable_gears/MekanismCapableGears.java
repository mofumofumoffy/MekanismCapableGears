package moffy.mekanism_capable_gears;

import com.mojang.logging.LogUtils;
import mekanism.api.providers.IModuleDataProvider;
import mekanism.common.Mekanism;
import mekanism.common.registries.MekanismModules;
import moffy.mekanism_capable_gears.testitem.TestItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.InterModComms;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

@Mod(MekanismCapableGears.MODID)
public class MekanismCapableGears {
    public static final String MODID = "mekanism_capable_gears";
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MODID);

    public static RegistryObject<Item> TEST_ITEM = null;

    public MekanismCapableGears(FMLJavaModLoadingContext context) {
        IEventBus bus = context.getModEventBus();
        MekaGearsModuleRegistry.INSTANCE.register(bus);
        bus.addListener(MCGEvent::onRegisterCaps);

        MinecraftForge.EVENT_BUS.addListener(MCGEvent::onModifyAttribute);


        TEST_ITEM = ITEMS.register("test_item", TestItem::new);
        ITEMS.register(bus);
        bus.addListener(this::commonSetup);
    }

    public void commonSetup(FMLCommonSetupEvent event){
        MekaGearsModuleRegistry.INSTANCE.registerModule(TEST_ITEM.get(), MekanismModules.ENERGY_UNIT, MekanismModules.ATTACK_AMPLIFICATION_UNIT, MekanismModules.SILK_TOUCH_UNIT, MekanismModules.FORTUNE_UNIT, MekanismModules.BLASTING_UNIT, MekanismModules.VEIN_MINING_UNIT,
                MekanismModules.FARMING_UNIT, MekanismModules.SHEARING_UNIT, MekanismModules.TELEPORTATION_UNIT, MekanismModules.EXCAVATION_ESCALATION_UNIT);
    }
}
