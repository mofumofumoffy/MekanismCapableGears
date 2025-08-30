package moffy.mekanism_capable_gears.mixin;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2BooleanArrayMap;
import it.unimi.dsi.fastutil.objects.Reference2BooleanMap;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.NBTConstants;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.event.MekanismTeleportEvent;
import mekanism.api.gear.ICustomModule;
import mekanism.api.gear.IModule;
import mekanism.api.math.FloatingLong;
import mekanism.api.radial.RadialData;
import mekanism.api.radial.mode.IRadialMode;
import mekanism.api.text.EnumColor;
import mekanism.client.key.MekKeyHandler;
import mekanism.client.key.MekanismKeyHandler;
import mekanism.common.Mekanism;
import mekanism.common.MekanismLang;
import mekanism.common.config.MekanismConfig;
import mekanism.common.content.gear.IBlastingItem;
import mekanism.common.content.gear.IModuleContainerItem;
import mekanism.common.content.gear.Module;
import mekanism.common.content.gear.mekatool.ModuleAttackAmplificationUnit;
import mekanism.common.content.gear.mekatool.ModuleExcavationEscalationUnit;
import mekanism.common.content.gear.mekatool.ModuleTeleportationUnit;
import mekanism.common.content.gear.mekatool.ModuleVeinMiningUnit;
import mekanism.common.lib.attribute.AttributeCache;
import mekanism.common.lib.radial.IGenericRadialModeItem;
import mekanism.common.network.to_client.PacketPortalFX;
import mekanism.common.registries.MekanismModules;
import mekanism.common.tags.MekanismTags;
import mekanism.common.util.ItemDataUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.StorageUtils;
import moffy.mekanism_capable_gears.IAttributeCacheAccessor;
import moffy.mekanism_capable_gears.IMekaGears;
import moffy.mekanism_capable_gears.MekaGearsCapability;
import moffy.mekanism_capable_gears.IMekaForgeItemHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Mixin(Item.class)
public class ItemMixin implements IModuleContainerItem, IGenericRadialModeItem, IMekaForgeItemHandler, IAttributeCacheAccessor {

    @Unique
    private static final UUID ATTACK_DAMAGE_ADDITION = UUID.fromString("435fee9c-f619-40fd-96a3-f9a7d75ec04a");

    @Unique
    private static final UUID ATTACK_SPEED_ADDITION = UUID.fromString("d94399a2-ce80-42cb-855d-29d1e93dc588");

    @Unique
    private Int2ObjectMap<AttributeCache> mekanism_capable_tool$attributeCaches;

    @Inject(
            at = @At("TAIL"),
            method = "<init>"
    )
    public void initAttributeCache(Item.Properties properties, CallbackInfo ci){
        mekanism_capable_tool$attributeCaches = new Int2ObjectArrayMap<>(ModuleAttackAmplificationUnit.AttackDamage.values().length);
    }

    @Inject(
            at=@At("RETURN"),
            method="appendHoverText"
    )
    public void appendHoverText(ItemStack stack, Level level, List<Component> components, TooltipFlag tooltipFlag, CallbackInfo ci) {
        if(stack.getCapability(MekaGearsCapability.MEKA_GEARS_CAPABILITY).isPresent()){
            if (MekKeyHandler.isKeyPressed(MekanismKeyHandler.detailsKey)) {
                addModuleDetails(stack, components);
            } else {
                StorageUtils.addStoredEnergy(stack, components, true);
                components.add(MekanismLang.HOLD_FOR_MODULES.translateColored(EnumColor.GRAY, EnumColor.INDIGO, MekanismKeyHandler.detailsKey.getTranslatedKeyMessage()));
            }
        }
    }

    @Inject(
            at=@At("RETURN"),
            method="useOn",
            cancellable = true
    )
    public void useOn(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        for (Module<?> module : getModules(context.getItemInHand())) {
            if (module.isEnabled()) {
                InteractionResult result = mekanism_capable_tool$onModuleUse(module, context);
                if (result != InteractionResult.PASS) {
                    cir.setReturnValue(result);
                }
            }
        }
    }

    @Inject(
            at=@At("RETURN"),
            method="interactLivingEntity",
            cancellable = true
    )
    public void interactLivingEntity(ItemStack stack, Player player, LivingEntity entity, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        for (Module<?> module : getModules(stack)) {
            if (module.isEnabled()) {
                InteractionResult result = mekanism_capable_tool$onModuleInteract(module, player, entity, hand);
                if (result != InteractionResult.PASS) {
                    cir.setReturnValue(result);
                }
            }
        }
    }

    @Unique
    private <MODULE extends ICustomModule<MODULE>> InteractionResult mekanism_capable_tool$onModuleInteract(IModule<MODULE> module, @NotNull Player player, @NotNull LivingEntity entity,
                                                                                                            @NotNull InteractionHand hand) {
        return module.getCustomInstance().onInteract(module, player, entity, hand);
    }

    @Inject(
            at=@At("RETURN"),
            method="getDestroySpeed",
            cancellable = true
    )
    public void getDestroySpeed(ItemStack stack, BlockState state, CallbackInfoReturnable<Float> cir) {
        if(stack.getCapability(MekaGearsCapability.MEKA_GEARS_CAPABILITY).isPresent()){
            IEnergyContainer energyContainer = StorageUtils.getEnergyContainer(stack, 0);
            if (energyContainer == null) {
                cir.setReturnValue(0f);
            }
            FloatingLong energyRequired = mekanism_capable_tool$getDestroyEnergy(stack, state.destroySpeed, isModuleEnabled(stack, MekanismModules.SILK_TOUCH_UNIT));
            FloatingLong energyAvailable = Objects.requireNonNull(energyContainer).extract(energyRequired, Action.SIMULATE, AutomationType.MANUAL);
            if (energyAvailable.smallerThan(energyRequired)) {
                cir.setReturnValue(MekanismConfig.gear.mekaToolBaseEfficiency.get() * energyAvailable.divide(energyRequired).floatValue());
            }
            IModule<ModuleExcavationEscalationUnit> module = getModule(stack, MekanismModules.EXCAVATION_ESCALATION_UNIT);
            cir.setReturnValue(module == null || !module.isEnabled() ? MekanismConfig.gear.mekaToolBaseEfficiency.get() : module.getCustomInstance().getEfficiency());
        }
    }

    @Inject(
            at=@At("RETURN"),
            method="mineBlock",
            cancellable = true
    )
    public void mineBlock(ItemStack stack, Level world, BlockState state, BlockPos pos, LivingEntity entity, CallbackInfoReturnable<Boolean> cir){
        if(stack.getCapability(MekaGearsCapability.MEKA_GEARS_CAPABILITY).isPresent()){
            IEnergyContainer energyContainer = StorageUtils.getEnergyContainer(stack, 0);
            if (energyContainer != null) {
                FloatingLong energyRequired = mekanism_capable_tool$getDestroyEnergy(stack, state.getDestroySpeed(world, pos), isModuleEnabled(stack, MekanismModules.SILK_TOUCH_UNIT));
                energyContainer.extract(energyRequired, Action.EXECUTE, AutomationType.MANUAL);
            }
            cir.setReturnValue(true);
        }
    }

    @Inject(
            at=@At("RETURN"),
            method="hurtEnemy",
            cancellable = true
    )
    public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker, CallbackInfoReturnable<Boolean> cir) {
        if(stack.getCapability(MekaGearsCapability.MEKA_GEARS_CAPABILITY).isPresent()){
            IModule<ModuleAttackAmplificationUnit> attackAmplificationUnit = getModule(stack, MekanismModules.ATTACK_AMPLIFICATION_UNIT);
            if (attackAmplificationUnit != null && attackAmplificationUnit.isEnabled()) {
                //Note: We only have an energy cost if the damage is above base, so we can skip all those checks
                // if we don't have an enabled attack amplification unit
                int unitDamage = attackAmplificationUnit.getCustomInstance().getDamage();
                if (unitDamage > 0) {
                    IEnergyContainer energyContainer = StorageUtils.getEnergyContainer(stack, 0);
                    if (energyContainer != null && !energyContainer.isEmpty()) {
                        //Try to extract full energy, even if we have a lower damage amount this is fine as that just means
                        // we don't have enough energy, but we will remove as much as we can, which is how much corresponds
                        // to the amount of damage we will actually do
                        energyContainer.extract(MekanismConfig.gear.mekaToolEnergyUsageWeapon.get().multiply(unitDamage / 4D), Action.EXECUTE, AutomationType.MANUAL);
                    }
                }
            }
            cir.setReturnValue(true);
        }
    }

    @Unique
    private Object2IntMap<BlockPos> mekanism_capable_tool$getVeinedBlocks(Level world, ItemStack stack, Map<BlockPos, BlockState> blocks, Reference2BooleanMap<Block> oreTracker) {
        IModule<ModuleVeinMiningUnit> veinMiningUnit = getModule(stack, MekanismModules.VEIN_MINING_UNIT);
        if (veinMiningUnit != null && veinMiningUnit.isEnabled()) {
            ModuleVeinMiningUnit customInstance = veinMiningUnit.getCustomInstance();
            return ModuleVeinMiningUnit.findPositions(world, blocks, customInstance.isExtended() ? customInstance.getExcavationRange() : 0, oreTracker);
        }
        return blocks.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, be -> 0, (l, r) -> l, Object2IntArrayMap::new));
    }

    @Inject(
            at=@At("RETURN"),
            method="use",
            cancellable = true
    )
    public void use(Level world, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        ItemStack stack = player.getItemInHand(hand);
        if(stack.getCapability(MekaGearsCapability.MEKA_GEARS_CAPABILITY).isPresent()){
            if (!world.isClientSide()) {
                IModule<ModuleTeleportationUnit> module = getModule(stack, MekanismModules.TELEPORTATION_UNIT);
                if (module != null && module.isEnabled()) {
                    BlockHitResult result = MekanismUtils.rayTrace(player, MekanismConfig.gear.mekaToolMaxTeleportReach.get());
                    if (!module.getCustomInstance().requiresBlockTarget() || result.getType() != HitResult.Type.MISS) {
                        BlockPos pos = result.getBlockPos();
                        if (mekanism_capable_tool$isValidDestinationBlock(world, pos.above()) && mekanism_capable_tool$isValidDestinationBlock(world, pos.above(2))) {
                            double distance = player.distanceToSqr(pos.getX(), pos.getY(), pos.getZ());
                            if (distance < 5) {
                                cir.setReturnValue(InteractionResultHolder.pass(stack));
                            }
                            IEnergyContainer energyContainer = StorageUtils.getEnergyContainer(stack, 0);
                            FloatingLong energyNeeded = MekanismConfig.gear.mekaToolEnergyUsageTeleport.get().multiply(distance / 10D);
                            if (energyContainer == null || energyContainer.getEnergy().smallerThan(energyNeeded)) {
                                cir.setReturnValue(InteractionResultHolder.fail(stack));
                            }
                            double targetX = pos.getX() + 0.5;
                            double targetY = pos.getY() + 1.5;
                            double targetZ = pos.getZ() + 0.5;
                            MekanismTeleportEvent.MekaTool event = new MekanismTeleportEvent.MekaTool(player, targetX, targetY, targetZ, stack, result);
                            if (MinecraftForge.EVENT_BUS.post(event)) {
                                cir.setReturnValue(InteractionResultHolder.fail(stack));
                            }
                            Objects.requireNonNull(energyContainer).extract(energyNeeded, Action.EXECUTE, AutomationType.MANUAL);
                            if (player.isPassenger()) {
                                player.dismountTo(targetX, targetY, targetZ);
                            } else {
                                player.teleportTo(targetX, targetY, targetZ);
                            }
                            player.resetFallDistance();
                            Mekanism.packetHandler().sendToAllTracking(new PacketPortalFX(pos.above()), world, pos);
                            world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
                            cir.setReturnValue(InteractionResultHolder.success(stack));
                        }
                    }
                }
            }
            cir.setReturnValue(InteractionResultHolder.pass(stack));
        }
    }

    @Override
    public boolean supportsSlotType(ItemStack stack, @NotNull EquipmentSlot slotType) {
        return IGenericRadialModeItem.super.supportsSlotType(stack, slotType) && getModules(stack).stream().anyMatch(Module::handlesAnyModeChange);
    }

    @Nullable
    @Override
    public Component getScrollTextComponent(@NotNull ItemStack stack) {
        if(stack.getCapability(MekaGearsCapability.MEKA_GEARS_CAPABILITY).isPresent()){
            return getModules(stack).stream().filter(Module::handlesModeChange).findFirst().map(module -> module.getModeScrollComponent(stack)).orElse(null);
        }
        return IGenericRadialModeItem.super.getScrollTextComponent(stack);
    }

    @Unique
    private boolean mekanism_capable_tool$isValidDestinationBlock(Level world, BlockPos pos) {
        BlockState blockState = world.getBlockState(pos);
        //Allow teleporting into air or fluids
        return blockState.isAir() || MekanismUtils.isLiquidBlock(blockState.getBlock());
    }

    @Unique
    private FloatingLong mekanism_capable_tool$getDestroyEnergy(boolean silk) {
        return silk ? MekanismConfig.gear.mekaToolEnergyUsageSilk.get() : MekanismConfig.gear.mekaToolEnergyUsage.get();
    }

    @Unique
    private FloatingLong mekanism_capable_tool$getDestroyEnergy(ItemStack itemStack, float hardness, boolean silk) {
        return mekanism_capable_tool$getDestroyEnergy(mekanism_capable_tool$getDestroyEnergy(itemStack, silk), hardness);
    }

    @Unique
    private FloatingLong mekanism_capable_tool$getDestroyEnergy(FloatingLong baseDestroyEnergy, float hardness) {
        return hardness == 0 ? baseDestroyEnergy.divide(2) : baseDestroyEnergy;
    }

    @Unique
    private FloatingLong mekanism_capable_tool$getDestroyEnergy(ItemStack itemStack, boolean silk) {
        FloatingLong destroyEnergy = mekanism_capable_tool$getDestroyEnergy(silk);
        IModule<ModuleExcavationEscalationUnit> module = getModule(itemStack, MekanismModules.EXCAVATION_ESCALATION_UNIT);
        float efficiency = module == null || !module.isEnabled() ? MekanismConfig.gear.mekaToolBaseEfficiency.get() : module.getCustomInstance().getEfficiency();
        return destroyEnergy.multiply(efficiency);
    }

    @Unique
    private <MODULE extends ICustomModule<MODULE>> InteractionResult mekanism_capable_tool$onModuleUse(IModule<MODULE> module, UseOnContext context) {
        return module.getCustomInstance().onItemUse(module, context);
    }

    @Unique
    private <MODULE extends ICustomModule<MODULE>> boolean mekanism_capable_tool$canPerformAction(IModule<MODULE> module, ToolAction action) {
        return module.getCustomInstance().canPerformAction(module, action);
    }

    @Override
    public @Nullable RadialData<?> getRadialData(ItemStack stack) {
        LazyOptional<IMekaGears> mekaGearsCapabilityLazyOptional = stack.getCapability(MekaGearsCapability.MEKA_GEARS_CAPABILITY);
        if(mekaGearsCapabilityLazyOptional.isPresent()){
            IMekaGears mekaGearsCapability = mekaGearsCapabilityLazyOptional.orElseThrow(IllegalStateException::new);
            return mekaGearsCapability.getRadialData(stack);
        }
        return null;
    }

    @Override
    public <M extends IRadialMode> @Nullable M getMode(ItemStack stack, RadialData<M> radialData) {
        LazyOptional<IMekaGears> mekaGearsCapabilityLazyOptional = stack.getCapability(MekaGearsCapability.MEKA_GEARS_CAPABILITY);
        if(mekaGearsCapabilityLazyOptional.isPresent()){
            IMekaGears mekaGearsCapability = mekaGearsCapabilityLazyOptional.orElseThrow(IllegalStateException::new);
            return mekaGearsCapability.getMode(stack, radialData);
        }
        return null;
    }

    @Override
    public <M extends IRadialMode> void setMode(ItemStack stack, Player player, RadialData<M> radialData, M mode) {
        LazyOptional<IMekaGears> mekaGearsCapabilityLazyOptional = stack.getCapability(MekaGearsCapability.MEKA_GEARS_CAPABILITY);
        if(mekaGearsCapabilityLazyOptional.isPresent()){
            IMekaGears mekaGearsCapability = mekaGearsCapabilityLazyOptional.orElseThrow(IllegalStateException::new);
            mekaGearsCapability.setMode(stack, player, radialData, mode);
        }
    }

    @Override
    public void changeMode(@NotNull Player player, @NotNull ItemStack stack, int shift, DisplayChange displayChange) {
        LazyOptional<IMekaGears> mekaGearsCapabilityLazyOptional = stack.getCapability(MekaGearsCapability.MEKA_GEARS_CAPABILITY);
        if (mekaGearsCapabilityLazyOptional.isPresent()) {
            IMekaGears mekaGearsCapability = mekaGearsCapabilityLazyOptional.orElseThrow(IllegalStateException::new);
            mekaGearsCapability.changeMode(player, stack, shift, displayChange);
        }
    }

    @Unique
    private Item mekanism_capable_tool$item(){
        return ((Item)((Object)this));
    }

    @Override
    public Int2ObjectMap<AttributeCache> mekanism_capable_tool$getAttributeCaches() {
        return this.mekanism_capable_tool$attributeCaches;
    }

    @Override
    public boolean onBlockStartBreak(ItemStack stack, BlockPos pos, Player player) {
        LazyOptional<IMekaGears> mekaGearsCapabilityLazyOptional = stack.getCapability(MekaGearsCapability.MEKA_GEARS_CAPABILITY);
        if(mekaGearsCapabilityLazyOptional.isPresent()){
            IMekaGears capability = mekaGearsCapabilityLazyOptional.orElseThrow(IllegalStateException::new);

            if (player.level().isClientSide || player.isCreative()) {
                return false;
            }
            IEnergyContainer energyContainer = StorageUtils.getEnergyContainer(stack, 0);
            if (energyContainer != null) {
                Level world = player.level();
                BlockState state = world.getBlockState(pos);
                boolean silk = ((IModuleContainerItem)stack.getItem()).isModuleEnabled(stack, MekanismModules.SILK_TOUCH_UNIT);
                FloatingLong modDestroyEnergy = mekanism_capable_tool$getDestroyEnergy(stack, silk);
                FloatingLong energyRequired = mekanism_capable_tool$getDestroyEnergy(modDestroyEnergy, state.getDestroySpeed(world, pos));
                if (energyContainer.extract(energyRequired, Action.SIMULATE, AutomationType.MANUAL).greaterOrEqual(energyRequired) && capability instanceof IBlastingItem blastingCapability) {
                    Map<BlockPos, BlockState> blocks = blastingCapability.getBlastedBlocks(world, player, stack, pos, state);
                    blocks = blocks.isEmpty() && ModuleVeinMiningUnit.canVeinBlock(state) ? Map.of(pos, state) : blocks;

                    Reference2BooleanMap<Block> oreTracker = blocks.values().stream().collect(Collectors.toMap(BlockBehaviour.BlockStateBase::getBlock,
                            bs -> bs.is(MekanismTags.Blocks.ATOMIC_DISASSEMBLER_ORE), (l, r) -> l, Reference2BooleanArrayMap::new));

                    Object2IntMap<BlockPos> veinedBlocks = mekanism_capable_tool$getVeinedBlocks(world, stack, blocks, oreTracker);
                    if (!veinedBlocks.isEmpty()) {
                        FloatingLong baseDestroyEnergy = mekanism_capable_tool$getDestroyEnergy(silk);
                        MekanismUtils.veinMineArea(energyContainer, energyRequired, world, pos, (ServerPlayer) player, stack, stack.getItem(), veinedBlocks,
                                hardness -> mekanism_capable_tool$getDestroyEnergy(modDestroyEnergy, hardness),
                                (hardness, distance, bs) -> mekanism_capable_tool$getDestroyEnergy(baseDestroyEnergy, hardness).multiply(0.5 * Math.pow(distance, oreTracker.getBoolean(bs.getBlock()) ? 1.5 : 2)));
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean canPerformAction(ItemStack stack, ToolAction action) {
        return getModules(stack).stream().anyMatch(module -> module.isEnabled() && mekanism_capable_tool$canPerformAction(module, action));
    }

    @Override
    public boolean isNotReplaceableByPickAction(ItemStack stack, Player player, int inventorySlot) {
        boolean result = stack.isEnchanted();
        if(stack.getCapability(MekaGearsCapability.MEKA_GEARS_CAPABILITY).isPresent()){
            return result || ItemDataUtils.hasData(stack, NBTConstants.MODULES, Tag.TAG_COMPOUND);
        }
        return result;
    }

    @Override
    public int getEnchantmentLevel(ItemStack stack, Enchantment enchantment) {
        int result = EnchantmentHelper.getTagEnchantmentLevel(enchantment, stack);
        if(!stack.isEmpty() && stack.getCapability(MekaGearsCapability.MEKA_GEARS_CAPABILITY).isPresent()){
            ListTag enchantments = ItemDataUtils.getList(stack, NBTConstants.ENCHANTMENTS);
            return Math.max(MekanismUtils.getEnchantmentLevel(enchantments, enchantment), result);
        }
        return result;
    }

    @Override
    public Map<Enchantment, Integer> getAllEnchantments(ItemStack stack) {
        Map<Enchantment, Integer> result = EnchantmentHelper.deserializeEnchantments(stack.getEnchantmentTags());
        if(stack.getCapability(MekaGearsCapability.MEKA_GEARS_CAPABILITY).isPresent()){
            Map<Enchantment, Integer> enchantments = EnchantmentHelper.deserializeEnchantments(ItemDataUtils.getList(stack, NBTConstants.ENCHANTMENTS));
            result.forEach((enchantment, level) -> enchantments.merge(enchantment, level, Math::max));
            return enchantments;
        }
        return result;
    }
}
