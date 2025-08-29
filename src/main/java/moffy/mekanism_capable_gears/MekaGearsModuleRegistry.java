package moffy.mekanism_capable_gears;

import mekanism.api.gear.IModule;
import mekanism.api.providers.IModuleDataProvider;
import mekanism.common.Mekanism;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.InterModComms;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.*;

public final class MekaGearsModuleRegistry {
    public static final MekaGearsModuleRegistry INSTANCE = new MekaGearsModuleRegistry();

    private final Set<ModuleInfo> infoSet;

    public MekaGearsModuleRegistry(){
        this.infoSet = new HashSet<>();
    }

    public void register(IEventBus bus){
        bus.addListener(this::enqueueIMC);
    }

    public void registerModule(Item item, IModuleDataProvider<?>... providers){
        infoSet.add(new ModuleInfo(item, providers));
    }

    public Set<ModuleInfo> getInfoSet() {
        return infoSet;
    }

    private void enqueueIMC(InterModEnqueueEvent event){
        infoSet.forEach(moduleInfo -> {
            addModules(moduleInfo.getName(), moduleInfo.providers());
        });
    }

    private static void addModules(String method, IModuleDataProvider<?>... moduleDataProviders) {
        sendModuleIMC(method, moduleDataProviders);
    }

    private static void sendModuleIMC(String method, IModuleDataProvider<?>... moduleDataProviders) {
        if (moduleDataProviders == null || moduleDataProviders.length == 0) {
            throw new IllegalArgumentException("No module data providers given.");
        }
        InterModComms.sendTo(Mekanism.MODID, method, () -> moduleDataProviders);
    }

    public record ModuleInfo(Item item, IModuleDataProvider<?>[] providers){
        public String getName(){
            return "adding_"+ Objects.requireNonNull(ForgeRegistries.ITEMS.getKey(item)) +"_modules";
        }
    }
}
