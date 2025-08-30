package moffy.mekanism_capable_gears;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import mekanism.common.content.gear.mekatool.ModuleAttackAmplificationUnit;
import mekanism.common.lib.attribute.AttributeCache;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.Event;

public class RegisterAttributeCacheEvent extends Event {
    private final Item item;
    private Int2ObjectMap<AttributeCache> attributeCaches;

    public RegisterAttributeCacheEvent(Item item) {
        this.item = item;
        this.attributeCaches = new Int2ObjectArrayMap<>(ModuleAttackAmplificationUnit.AttackDamage.values().length);
    }

    public Int2ObjectMap<AttributeCache> getAttributeCaches() {
        return attributeCaches;
    }

    public void setAttributeCaches(Int2ObjectMap<AttributeCache> attributeCaches){
        this.attributeCaches = attributeCaches;
    }

    public Item getItem() {
        return item;
    }
}
