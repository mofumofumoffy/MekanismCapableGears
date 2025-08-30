package moffy.mekanism_capable_gears;

import com.google.common.collect.ImmutableMultimap;
import it.unimi.dsi.fastutil.objects.*;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.gear.IModule;
import mekanism.api.math.FloatingLong;
import mekanism.common.config.MekanismConfig;
import mekanism.common.content.gear.IBlastingItem;
import mekanism.common.content.gear.IModuleContainerItem;
import mekanism.common.content.gear.mekatool.ModuleAttackAmplificationUnit;
import mekanism.common.content.gear.mekatool.ModuleExcavationEscalationUnit;
import mekanism.common.content.gear.mekatool.ModuleVeinMiningUnit;
import mekanism.common.lib.attribute.AttributeCache;
import mekanism.common.registries.MekanismModules;
import mekanism.common.tags.MekanismTags;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.StorageUtils;
import mekanism.common.util.WorldUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.level.BlockEvent;

import java.util.Map;
import java.util.stream.Collectors;

public class MCGEvent {
    public static void onRegisterCaps(RegisterCapabilitiesEvent event) {
        event.register(MekaGearsCapability.class);
    }

    public static void onModifyAttribute(ItemAttributeModifierEvent event){
        ItemStack stack = event.getItemStack();
        EquipmentSlot slot = event.getSlotType();
        if(stack.getCapability(MekaGearsCapability.MEKA_GEARS_CAPABILITY).isPresent()){
            if (slot == EquipmentSlot.MAINHAND) {
                int unitDamage = 0;
                IModule<ModuleAttackAmplificationUnit> attackAmplificationUnit = ((IModuleContainerItem)stack.getItem()).getModule(stack, MekanismModules.ATTACK_AMPLIFICATION_UNIT);
                if (attackAmplificationUnit != null && attackAmplificationUnit.isEnabled()) {
                    unitDamage = attackAmplificationUnit.getCustomInstance().getDamage();
                    if (unitDamage > 0) {
                        FloatingLong energyCost = MekanismConfig.gear.mekaToolEnergyUsageWeapon.get().multiply(unitDamage / 4D);
                        IEnergyContainer energyContainer = StorageUtils.getEnergyContainer(stack, 0);
                        FloatingLong energy = energyContainer == null ? FloatingLong.ZERO : energyContainer.getEnergy();
                        if (energy.smallerThan(energyCost)) {
                            double bonusDamage = unitDamage * energy.divideToLevel(energyCost);
                            if (bonusDamage > 0) {
                                ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
                                builder.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(IAttributeCacheAccessor.ATTACK_DAMAGE_ADDITION, "Weapon modifier",
                                        MekanismConfig.gear.mekaToolBaseDamage.get() + bonusDamage, AttributeModifier.Operation.ADDITION));
                                builder.put(Attributes.ATTACK_SPEED, new AttributeModifier(IAttributeCacheAccessor.ATTACK_SPEED_ADDITION, "Weapon modifier",
                                        MekanismConfig.gear.mekaToolAttackSpeed.get(), AttributeModifier.Operation.ADDITION));
                                builder.build().forEach(event::addModifier);
                                return;
                            }
                            unitDamage = 0;
                        }
                    }
                }
                ((IAttributeCacheAccessor)stack.getItem()).mekanism_capable_tool$getAttributeCaches().computeIfAbsent(unitDamage, damage -> new AttributeCache(builder -> {
                    builder.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(IAttributeCacheAccessor.ATTACK_DAMAGE_ADDITION, "Weapon modifier",
                            damage, AttributeModifier.Operation.ADDITION));
                    builder.put(Attributes.ATTACK_SPEED, new AttributeModifier(IAttributeCacheAccessor.ATTACK_SPEED_ADDITION, "Weapon modifier",
                            0, AttributeModifier.Operation.ADDITION));
                }, MekanismConfig.gear.mekaToolBaseDamage, MekanismConfig.gear.mekaToolAttackSpeed)).get().forEach(event::addModifier);
            }
        }
    }
}
