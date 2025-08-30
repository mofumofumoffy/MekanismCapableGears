package moffy.mekanism_capable_gears;

import com.google.common.collect.ImmutableMultimap;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.functions.FloatSupplier;
import mekanism.api.gear.IModule;
import mekanism.api.gear.IModuleHelper;
import mekanism.api.math.FloatingLong;
import mekanism.api.math.FloatingLongSupplier;
import mekanism.common.Mekanism;
import mekanism.common.base.KeySync;
import mekanism.common.config.MekanismConfig;
import mekanism.common.content.gear.IBlastingItem;
import mekanism.common.content.gear.IModuleContainerItem;
import mekanism.common.content.gear.mekasuit.ModuleHydraulicPropulsionUnit;
import mekanism.common.content.gear.mekasuit.ModuleLocomotiveBoostingUnit;
import mekanism.common.content.gear.mekatool.ModuleAttackAmplificationUnit;
import mekanism.common.integration.curios.CuriosIntegration;
import mekanism.common.item.interfaces.IJetpackItem;
import mekanism.common.lib.attribute.AttributeCache;
import mekanism.common.lib.radiation.RadiationManager;
import mekanism.common.registries.MekanismGameEvents;
import mekanism.common.registries.MekanismModules;
import mekanism.common.util.StorageUtils;
import moffy.mekanism_capable_gears.interfaces.IAbsorbableItem;
import moffy.mekanism_capable_gears.interfaces.IAttributeCacheAccessor;
import moffy.mekanism_capable_gears.interfaces.IMekaGear;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

public class MCGEvent {
    public static void onRegisterCaps(RegisterCapabilitiesEvent event) {
        event.register(IMekaGear.class);
    }

    public static void onModifyAttribute(ItemAttributeModifierEvent event){
        ItemStack stack = event.getItemStack();
        EquipmentSlot slot = event.getSlotType();
        if(stack.getCapability(MekaGearCapability.MEKA_GEAR_CAPABILITY).isPresent()){
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
        } else if(slot != EquipmentSlot.OFFHAND){
            ((IAttributeCacheAccessor)stack.getItem()).mekanism_capable_tool$getAttributeCaches().forEach((integer, attributeCache) -> {
                attributeCache.get().forEach(event::addModifier);
            });
        }
    }

    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.side.isServer()) {
            tickEnd(event.player);
        }
    }

    public static void onEntityAttack(LivingAttackEvent event) {
        LivingEntity entity = event.getEntity();
        if (event.getAmount() <= 0 || !entity.isAlive()) {
            return;
        }
        if (event.getSource().is(DamageTypeTags.IS_FALL)) {
            FallEnergyInfo info = getFallAbsorptionEnergyInfo(entity);
            if (info != null && tryAbsorbAll(event, info.container, info.damageRatio, info.energyCost)) {
                return;
            }
        }
        if (entity instanceof Player player) {
            if (IAbsorbableItem.tryAbsorbAll(player, event.getSource(), event.getAmount())) {
                event.setCanceled(true);
            }
        }
    }

    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity entity = event.getEntity();
        if (event.getAmount() <= 0 || !entity.isAlive()) {
            return;
        }
        if (event.getSource().is(DamageTypeTags.IS_FALL)) {
            FallEnergyInfo info = getFallAbsorptionEnergyInfo(entity);
            if (info != null && handleDamage(event, info.container, info.damageRatio, info.energyCost)) {
                return;
            }
        }
        if (entity instanceof Player player) {
            float ratioAbsorbed = IAbsorbableItem.getDamageAbsorbed(
                    player,
                    event.getSource(),
                    event.getAmount()
            );
            if (ratioAbsorbed > 0) {
                float damageRemaining = event.getAmount() * Math.max(0, 1 - ratioAbsorbed);
                if (damageRemaining <= 0) {
                    event.setCanceled(true);
                } else {
                    event.setAmount(damageRemaining);
                }
            }
        }
    }

    public static void getBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        float speed = event.getNewSpeed();

        Optional<BlockPos> position = event.getPosition();
        if (position.isPresent()) {
            BlockPos pos = position.get();

            ItemStack mainHand = player.getMainHandItem();
            if (!mainHand.isEmpty()) {
                LazyOptional<IMekaGear>mekaGearLazyOptional = mainHand.getCapability(MekaGearCapability.MEKA_GEAR_CAPABILITY);
                if(mekaGearLazyOptional.isPresent()){
                    IMekaGear mekaGear = mekaGearLazyOptional.orElseThrow(IllegalStateException::new);
                    if(mekaGear instanceof IBlastingItem blastingGear){
                        Map<BlockPos, BlockState> blocks = blastingGear.getBlastedBlocks(
                                player.level(),
                                player,
                                mainHand,
                                pos,
                                event.getState()
                        );
                        if (!blocks.isEmpty()) {
                            float targetHardness = event.getState().getDestroySpeed(player.level(), pos);
                            float maxHardness = blocks
                                    .entrySet()
                                    .stream()
                                    .map(entry -> entry.getValue().getDestroySpeed(player.level(), entry.getKey()))
                                    .reduce(targetHardness, Float::max);
                            speed *= (targetHardness / maxHardness);
                        }
                    }
                }
            }
        }

        ItemStack legs = player.getItemBySlot(EquipmentSlot.LEGS);
        if (!legs.isEmpty() && IModuleHelper.INSTANCE.isEnabled(legs, MekanismModules.GYROSCOPIC_STABILIZATION_UNIT)) {
            if (player.isEyeInFluidType(ForgeMod.WATER_TYPE.get()) && !EnchantmentHelper.hasAquaAffinity(player)) {
                speed *= 5.0F;
            }

            if (!player.onGround()) {
                speed *= 5.0F;
            }
        }

        event.setNewSpeed(speed);
    }

    public static void onLivingJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof Player player) {
            IModule<ModuleHydraulicPropulsionUnit> module = IModuleHelper.INSTANCE.load(
                    player.getItemBySlot(EquipmentSlot.FEET),
                    MekanismModules.HYDRAULIC_PROPULSION_UNIT
            );
            if (module != null && module.isEnabled() && Mekanism.keyMap.has(player.getUUID(), KeySync.BOOST)) {
                float boost = module.getCustomInstance().getBoost();
                FloatingLong usage = MekanismConfig.gear.mekaSuitBaseJumpEnergyUsage.get().multiply(boost / 0.1F);
                IEnergyContainer energyContainer = module.getEnergyContainer();
                if (module.canUseEnergy(player, energyContainer, usage, false)) {
                    IModule<ModuleLocomotiveBoostingUnit> boostModule = IModuleHelper.INSTANCE.load(
                            player.getItemBySlot(EquipmentSlot.LEGS),
                            MekanismModules.LOCOMOTIVE_BOOSTING_UNIT
                    );
                    if (
                            boostModule != null &&
                                    boostModule.isEnabled() &&
                                    boostModule.getCustomInstance().canFunction(boostModule, player)
                    ) {
                        boost = (float) Math.sqrt(boost);
                    }
                    player.setDeltaMovement(player.getDeltaMovement().add(0, boost, 0));
                    module.useEnergy(player, energyContainer, usage, true);
                }
            }
        }
    }

    private static void tickEnd(Player player) {
        Mekanism.playerState.updateStepAssist(player);
        Mekanism.playerState.updateSwimBoost(player);
        if (player instanceof ServerPlayer serverPlayer) {
            RadiationManager.get().tickServer(serverPlayer);
        }

        ItemStack currentItem = player.getInventory().getSelected();
        LazyOptional<IMekaGear> mekaGearLazyOptional = currentItem.getCapability(MekaGearCapability.MEKA_GEAR_CAPABILITY);
        if(mekaGearLazyOptional.isPresent()){
            IMekaGear mekaGear = mekaGearLazyOptional.orElseThrow(IllegalStateException::new);

            ItemStack jetpack = getActiveJetpack(player);
            if (!jetpack.isEmpty() && mekaGear instanceof IJetpackItem jetpackGear) {
                ItemStack primaryJetpack = IJetpackItem.getPrimaryJetpack(player);
                if (!primaryJetpack.isEmpty()) {
                    IJetpackItem.JetpackMode primaryMode = jetpackGear.getJetpackMode(primaryJetpack);
                    IJetpackItem.JetpackMode mode = IJetpackItem.getPlayerJetpackMode(player, primaryMode, () -> Mekanism.keyMap.has(player.getUUID(), KeySync.ASCEND));
                    if (mode != IJetpackItem.JetpackMode.DISABLED) {
                        if (IJetpackItem.handleJetpackMotion(player, mode, () -> Mekanism.keyMap.has(player.getUUID(), KeySync.ASCEND))) {
                            player.resetFallDistance();
                            if (player instanceof ServerPlayer serverPlayer) {
                                serverPlayer.connection.aboveGroundTickCount = 0;
                            }
                        }
                        ((IJetpackItem) jetpack.getItem()).useJetpackFuel(jetpack);
                        if (player.level().getGameTime() % 10 == 0) {
                            player.gameEvent(MekanismGameEvents.JETPACK_BURN.get());
                        }
                    }
                }
            }
        }

        Mekanism.playerState.updateFlightInfo(player);
    }

    private static ItemStack getActiveJetpack(LivingEntity entity) {
        return getJetpack(entity, stack -> {
            LazyOptional<IMekaGear> mekaGearLazyOptional = stack.getCapability(MekaGearCapability.MEKA_GEAR_CAPABILITY);
            if(mekaGearLazyOptional.isPresent()){
                IMekaGear mekaGear = mekaGearLazyOptional.orElseThrow(IllegalStateException::new);
                if(mekaGear instanceof IJetpackItem iJetpackGear){
                    return iJetpackGear.canUseJetpack(stack);
                }
            }
            return false;
        });
    }

    private static ItemStack getJetpack(LivingEntity entity, Predicate<ItemStack> matcher) {
        ItemStack chest = entity.getItemBySlot(EquipmentSlot.CHEST);
        if (matcher.test(chest)) {
            return chest;
        } else {
            return Mekanism.hooks.CuriosLoaded ? CuriosIntegration.findFirstCurio(entity, matcher) : ItemStack.EMPTY;
        }
    }

    private static @NotNull ItemStack getPrimaryJetpack(LivingEntity entity) {
        return getJetpack(entity, (stack) -> {
            LazyOptional<IMekaGear> mekaGearLazyOptional = stack.getCapability(MekaGearCapability.MEKA_GEAR_CAPABILITY);
            if(mekaGearLazyOptional.isPresent()){
                IMekaGear mekaGear = mekaGearLazyOptional.orElseThrow(IllegalStateException::new);
                return mekaGear instanceof IJetpackItem;
            }
            return false;
        });
    }

    private static boolean handleDamage(
            LivingHurtEvent event,
            @Nullable IEnergyContainer energyContainer,
            FloatSupplier absorptionRatio,
            FloatingLongSupplier energyCost
    ) {
        if (energyContainer != null) {
            float absorption = absorptionRatio.getAsFloat();
            float amount = event.getAmount() * absorption;
            FloatingLong energyRequirement = energyCost.get().multiply(amount);
            float ratioAbsorbed;
            if (energyRequirement.isZero()) {
                ratioAbsorbed = absorption;
            } else {
                ratioAbsorbed =
                        absorption *
                                energyContainer
                                        .extract(energyRequirement, Action.EXECUTE, AutomationType.MANUAL)
                                        .divide(amount)
                                        .floatValue();
            }
            if (ratioAbsorbed > 0) {
                float damageRemaining = event.getAmount() * Math.max(0, 1 - ratioAbsorbed);
                if (damageRemaining <= 0) {
                    event.setCanceled(true);
                    return true;
                } else {
                    event.setAmount(damageRemaining);
                }
            }
        }
        return false;
    }

    private static boolean tryAbsorbAll(LivingAttackEvent event, @Nullable IEnergyContainer energyContainer, FloatSupplier absorptionRatio, FloatingLongSupplier energyCost) {
        if (energyContainer != null && absorptionRatio.getAsFloat() == 1) {
            FloatingLong energyRequirement = energyCost.get().multiply(event.getAmount());
            if (energyRequirement.isZero()) {
                event.setCanceled(true);
                return true;
            }
            FloatingLong simulatedExtract = energyContainer.extract(energyRequirement, Action.SIMULATE, AutomationType.MANUAL);
            if (simulatedExtract.equals(energyRequirement)) {
                energyContainer.extract(energyRequirement, Action.EXECUTE, AutomationType.MANUAL);
                event.setCanceled(true);
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static FallEnergyInfo getFallAbsorptionEnergyInfo(LivingEntity base) {
        ItemStack feetStack = base.getItemBySlot(EquipmentSlot.FEET);
        if (!feetStack.isEmpty()) {
            LazyOptional<IMekaGear> mekaGearLazyOptional = feetStack.getCapability(MekaGearCapability.MEKA_GEAR_CAPABILITY);
            if(mekaGearLazyOptional.isPresent()){
                return new FallEnergyInfo(
                        StorageUtils.getEnergyContainer(feetStack, 0),
                        MekanismConfig.gear.mekaSuitFallDamageRatio,
                        MekanismConfig.gear.mekaSuitEnergyUsageFall
                );
            }
        }
        return null;
    }

    private record FallEnergyInfo(
            @Nullable IEnergyContainer container,
            FloatSupplier damageRatio,
            FloatingLongSupplier energyCost
    ) {}
}
