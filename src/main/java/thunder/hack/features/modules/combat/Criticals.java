package thunder.hack.features.modules.combat;

import io.netty.buffer.Unpooled;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.NotNull;
import thunder.hack.events.impl.PacketEvent;
import thunder.hack.injection.accesors.IClientPlayerEntity;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.utility.Timer;

import java.util.Random;

public final class Criticals extends Module {
    public final Setting<Mode> mode = new Setting<>("Mode", Mode.UpdatedNCP);
    // Настройки для PacketCriticals (упрощенные)
    public final Setting<Boolean> useFullPackets = new Setting<>("UseFullPackets", false, v -> mode.getValue() == Mode.PacketCriticals);
    public final Setting<Boolean> randomize = new Setting<>("Randomize", false, v -> mode.getValue() == Mode.PacketCriticals);

    public static boolean cancelCrit;
    
    private final Random random = new Random();

    public Criticals() {
        super("Criticals", Category.COMBAT);
    }

    @EventHandler
    public void onPacketSend(PacketEvent.@NotNull Send event) {
        if (event.getPacket() instanceof PlayerInteractEntityC2SPacket && getInteractType(event.getPacket()) == InteractType.ATTACK) {
            Entity ent = getEntity(event.getPacket());
            if (ent == null || ent instanceof EndCrystalEntity || cancelCrit)
                return;
            
            
            doCrit();
        }
    }

    public void doCrit() {
        if (isDisabled() || mc.player == null || mc.world == null)
            return;
        if ((mc.player.isOnGround() || mc.player.getAbilities().flying || mode.is(Mode.Grim) || mode.is(Mode.PacketCriticals)) && !mc.player.isInLava() && !mc.player.isSubmergedInWater()) {
            switch (mode.getValue()) {
                case OldNCP -> {
                    critPacket(0.00001058293536, false);
                    critPacket(0.00000916580235, false);
                    critPacket(0.00000010371854, false);
                }
                case Ncp -> {
                    critPacket(0.0625D, false);
                    critPacket(0., false);
                }
                case UpdatedNCP -> {
                    critPacket(0.000000271875, false);
                    critPacket(0., false);
                }
                case Strict -> {
                    critPacket(0.062600301692775, false);
                    critPacket(0.07260029960661, false);
                    critPacket(0., false);
                    critPacket(0., false);
                }
                case Grim -> {
                    if (!mc.player.isOnGround())
                        critPacket(-0.000001, true);

                }
                case PacketCriticals -> {
                    // Простые криты через пакеты движения
                    critPacket(0.0625, false);
                    critPacket(0.0, false);
                }
            }
        }
    }

    private void critPacket(double yDelta, boolean full) {
        if (mode.is(Mode.PacketCriticals)) {
            // Для PacketCriticals используем настройки
            if (useFullPackets.getValue()) {
                sendPacket(new PlayerMoveC2SPacket.Full(
                    mc.player.getX() + (randomize.getValue() ? (random.nextDouble() - 0.5) * 0.001 : 0),
                    mc.player.getY() + yDelta + (randomize.getValue() ? (random.nextDouble() - 0.5) * 0.001 : 0),
                    mc.player.getZ() + (randomize.getValue() ? (random.nextDouble() - 0.5) * 0.001 : 0),
                    ((IClientPlayerEntity) mc.player).getLastYaw(),
                    ((IClientPlayerEntity) mc.player).getLastPitch(),
                    false
                ));
            } else {
                sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                    mc.player.getX() + (randomize.getValue() ? (random.nextDouble() - 0.5) * 0.001 : 0),
                    mc.player.getY() + yDelta + (randomize.getValue() ? (random.nextDouble() - 0.5) * 0.001 : 0),
                    mc.player.getZ() + (randomize.getValue() ? (random.nextDouble() - 0.5) * 0.001 : 0),
                    false
                ));
            }
        } else {
            // Для других режимов используем старую логику
            if (!full)
                sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY() + yDelta, mc.player.getZ(), false));
            else
                sendPacket(new PlayerMoveC2SPacket.Full(mc.player.getX(), mc.player.getY() + yDelta, mc.player.getZ(), ((IClientPlayerEntity) mc.player).getLastYaw(), ((IClientPlayerEntity) mc.player).getLastPitch(), false));
        }
    }



    public static Entity getEntity(@NotNull PlayerInteractEntityC2SPacket packet) {
        PacketByteBuf packetBuf = new PacketByteBuf(Unpooled.buffer());
        packet.write(packetBuf);
        return mc.world.getEntityById(packetBuf.readVarInt());
    }

    public static InteractType getInteractType(@NotNull PlayerInteractEntityC2SPacket packet) {
        PacketByteBuf packetBuf = new PacketByteBuf(Unpooled.buffer());
        packet.write(packetBuf);
        packetBuf.readVarInt();
        return packetBuf.readEnumConstant(InteractType.class);
    }

    public enum InteractType {
        INTERACT, ATTACK, INTERACT_AT
    }

    @Override
    public void onDisable() {
        // Очистка не требуется для упрощенной версии
    }

    public enum Mode {
        Ncp, Strict, OldNCP, UpdatedNCP, Grim, PacketCriticals
    }
}
