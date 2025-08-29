package moffy.mekanism_capable_gears.mixin;

import com.google.common.collect.ImmutableSet;
import com.llamalad7.mixinextras.sugar.Local;
import mekanism.api.gear.ModuleData;
import mekanism.api.providers.IModuleDataProvider;
import mekanism.common.Mekanism;
import mekanism.common.content.gear.ModuleHelper;
import moffy.mekanism_capable_gears.MekaGearsModuleRegistry;
import net.minecraft.world.item.Item;
import net.minecraftforge.fml.event.lifecycle.InterModProcessEvent;
import net.minecraftforge.registries.RegistryObject;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.Set;

@Mixin(value = ModuleHelper.class, remap = false, priority = 900)
public class ModuleHelperMixin {
    @Shadow
    private void logDebugReceivedIMC(String imcMethod, String senderModId, IModuleDataProvider<?> moduleDataProvider) {}

    @Final
    @Shadow
    private Map<Item, Set<ModuleData<?>>> supportedModules;

    @Inject(
            method = "processIMC",
            at = @At(
                    value = "INVOKE",
                    ordinal = 4,
                    shift = At.Shift.AFTER,
                    target = "Lmekanism/common/content/gear/ModuleHelper;mapSupportedModules(Lnet/minecraftforge/fml/event/lifecycle/InterModProcessEvent;Ljava/lang/String;Lmekanism/api/providers/IItemProvider;Ljava/util/Map;)V"
            )
    )
    public void processIMC(InterModProcessEvent event, CallbackInfo ci, @Local Map<ModuleData<?>, ImmutableSet.Builder<Item>> supportedContainersBuilderMap){
        MekaGearsModuleRegistry.INSTANCE.getInfoSet().forEach(moduleInfo -> {
            mekanism_capable_tool$mixinMapSupportedModules(event, moduleInfo.getName(), moduleInfo.item(), supportedContainersBuilderMap);
        });
    }

    @Unique
    private void mekanism_capable_tool$mixinMapSupportedModules(
            InterModProcessEvent event,
            String imcMethod,
            Item item,
            Map<ModuleData<?>, ImmutableSet.Builder<Item>> supportedContainersBuilderMap
    ) {
        ImmutableSet.Builder<ModuleData<?>> supportedModulesBuilder = ImmutableSet.builder();
        event
                .getIMCStream(imcMethod::equals)
                .forEach(message -> {
                    Object body = message.messageSupplier().get();
                    if (body instanceof IModuleDataProvider<?> moduleDataProvider) {
                        supportedModulesBuilder.add(moduleDataProvider.getModuleData());
                        logDebugReceivedIMC(imcMethod, message.senderModId(), moduleDataProvider);
                    } else if (body instanceof IModuleDataProvider<?>[] providers) {
                        for (IModuleDataProvider<?> moduleDataProvider : providers) {
                            supportedModulesBuilder.add(moduleDataProvider.getModuleData());
                            logDebugReceivedIMC(imcMethod, message.senderModId(), moduleDataProvider);
                        }
                    } else {
                        Mekanism.logger.warn(
                                "Received IMC message for '{}' from mod '{}' with an invalid body.",
                                imcMethod,
                                message.senderModId()
                        );
                    }
                });
        Set<ModuleData<?>> supported = supportedModulesBuilder.build();
        if (!supported.isEmpty()) {
            supportedModules.put(item, supported);
            for (ModuleData<?> data : supported) {
                supportedContainersBuilderMap.computeIfAbsent(data, d -> ImmutableSet.builder()).add(item);
            }
        }
    }
}
