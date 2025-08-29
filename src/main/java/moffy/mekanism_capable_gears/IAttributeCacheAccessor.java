package moffy.mekanism_capable_gears;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import mekanism.common.lib.attribute.AttributeCache;
import org.spongepowered.asm.mixin.Unique;

import java.util.UUID;

public interface IAttributeCacheAccessor {
    public static final UUID ATTACK_DAMAGE_ADDITION = UUID.fromString("435fee9c-f619-40fd-96a3-f9a7d75ec04a");
    public static final UUID ATTACK_SPEED_ADDITION = UUID.fromString("d94399a2-ce80-42cb-855d-29d1e93dc588");
    public Int2ObjectMap<AttributeCache> mekanism_capable_tool$getAttributeCaches();
}
