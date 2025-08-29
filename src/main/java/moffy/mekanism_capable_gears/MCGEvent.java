package moffy.mekanism_capable_gears;

import com.google.common.collect.ImmutableMultimap;
import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2BooleanArrayMap;
import it.unimi.dsi.fastutil.objects.Reference2BooleanMap;
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
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.spongepowered.asm.mixin.Unique;

import java.util.Map;
import java.util.stream.Collectors;

public class MCGEvent {
    public static void onRegisterCaps(RegisterCapabilitiesEvent event) {
        event.register(MekaGearsCapability.class);
    }
    public static void onBlockBreak(BlockEvent.BreakEvent event){
        Player player = event.getPlayer();
        ItemStack stack = player.getMainHandItem();
        BlockPos pos = event.getPos();
        LazyOptional<MekaGearsCapability> mekaGearsCapabilityLazyOptional = stack.getCapability(MekaGearsCapability.MEKA_GEARS_CAPABILITY);
        if(mekaGearsCapabilityLazyOptional.isPresent()){
            MekaGearsCapability capability = mekaGearsCapabilityLazyOptional.orElseThrow(IllegalStateException::new);
            if(capability instanceof IBlastingItem blastingCapability){
                if (player.level().isClientSide || player.isCreative()) {
                    return;
                }
                IEnergyContainer energyContainer = StorageUtils.getEnergyContainer(stack, 0);
                if (energyContainer != null) {
                    Level world = player.level();
                    BlockState state = world.getBlockState(pos);
                    boolean silk = ((IModuleContainerItem)stack.getItem()).isModuleEnabled(stack, MekanismModules.SILK_TOUCH_UNIT);
                    FloatingLong modDestroyEnergy = getDestroyEnergy(stack, silk);
                    FloatingLong energyRequired = getDestroyEnergy(modDestroyEnergy, state.getDestroySpeed(world, pos));
                    if (energyContainer.extract(energyRequired, Action.SIMULATE, AutomationType.MANUAL).greaterOrEqual(energyRequired)) {
                        Map<BlockPos, BlockState> blocks = blastingCapability.getBlastedBlocks(world, player, stack, pos, state);
                        blocks = blocks.isEmpty() && ModuleVeinMiningUnit.canVeinBlock(state) ? Map.of(pos, state) : blocks;

                        Reference2BooleanMap<Block> oreTracker = blocks.values().stream().collect(Collectors.toMap(BlockBehaviour.BlockStateBase::getBlock,
                                bs -> bs.is(MekanismTags.Blocks.ATOMIC_DISASSEMBLER_ORE), (l, r) -> l, Reference2BooleanArrayMap::new));

                        Object2IntMap<BlockPos> veinedBlocks = getVeinedBlocks(world, stack, blocks, oreTracker);
                        if (!veinedBlocks.isEmpty()) {
                            FloatingLong baseDestroyEnergy = getDestroyEnergy(silk);
                            MekanismUtils.veinMineArea(energyContainer, energyRequired, world, pos, (ServerPlayer) player, stack, stack.getItem(), veinedBlocks,
                                    hardness -> getDestroyEnergy(modDestroyEnergy, hardness),
                                    (hardness, distance, bs) -> getDestroyEnergy(baseDestroyEnergy, hardness).multiply(0.5 * Math.pow(distance, oreTracker.getBoolean(bs.getBlock()) ? 1.5 : 2)));
                        }
                    }
                }
            }
        }
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

    private static FloatingLong getDestroyEnergy(boolean silk) {
        return silk ? MekanismConfig.gear.mekaToolEnergyUsageSilk.get() : MekanismConfig.gear.mekaToolEnergyUsage.get();
    }

    private static FloatingLong getDestroyEnergy(ItemStack itemStack, float hardness, boolean silk) {
        return getDestroyEnergy(getDestroyEnergy(itemStack, silk), hardness);
    }

    private static FloatingLong getDestroyEnergy(FloatingLong baseDestroyEnergy, float hardness) {
        return hardness == 0 ? baseDestroyEnergy.divide(2) : baseDestroyEnergy;
    }

    private static FloatingLong getDestroyEnergy(ItemStack itemStack, boolean silk) {
        FloatingLong destroyEnergy = getDestroyEnergy(silk);
        IModule<ModuleExcavationEscalationUnit> module = ((IModuleContainerItem)itemStack.getItem()).getModule(itemStack, MekanismModules.EXCAVATION_ESCALATION_UNIT);
        float efficiency = module == null || !module.isEnabled() ? MekanismConfig.gear.mekaToolBaseEfficiency.get() : module.getCustomInstance().getEfficiency();
        return destroyEnergy.multiply(efficiency);
    }

    private static Object2IntMap<BlockPos> getVeinedBlocks(Level world, ItemStack stack, Map<BlockPos, BlockState> blocks, Reference2BooleanMap<Block> oreTracker) {
        IModule<ModuleVeinMiningUnit> veinMiningUnit = ((IModuleContainerItem)stack.getItem()).getModule(stack, MekanismModules.VEIN_MINING_UNIT);
        if (veinMiningUnit != null && veinMiningUnit.isEnabled()) {
            ModuleVeinMiningUnit customInstance = veinMiningUnit.getCustomInstance();
            return ModuleVeinMiningUnit.findPositions(world, blocks, customInstance.isExtended() ? customInstance.getExcavationRange() : 0, oreTracker);
        }
        return blocks.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, be -> 0, (l, r) -> l, Object2IntArrayMap::new));
    }
}
