package thunder.hack.injection.accesors;

import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityVelocityUpdateS2CPacket.class)
public interface IEntityVelocityUpdateS2CPacket {
    @Accessor("velocityX")
    void setMotionX(int velocityX);

    @Accessor("velocityY")
    void setMotionY(int velocityY);

    @Accessor("velocityZ")
    void setMotionZ(int velocityZ);
}