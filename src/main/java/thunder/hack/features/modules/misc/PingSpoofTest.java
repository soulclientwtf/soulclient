package thunder.hack.features.modules.misc;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket;
import thunder.hack.ThunderHack;
import thunder.hack.events.impl.PacketEvent;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;

import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

public class PingSpoofTest extends Module {

    public final Setting<Float> ping = new Setting<>("Ping", 500f, 1f, 1500f);
    private final ConcurrentHashMap<KeepAliveC2SPacket, Long> packets = new ConcurrentHashMap<>();

    public PingSpoofTest() {
        super("PingSpoof", Category.MISC);
    }

    @EventHandler
    public void onPacketSend(PacketEvent.Send event) {
        if (!(event.getPacket() instanceof KeepAliveC2SPacket packet)) return;

        if (packets.containsKey(packet)) {
            packets.remove(packet);
            return;
        }

        packets.put(packet, System.currentTimeMillis());
        event.cancel();
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive event) {

        if (packets.isEmpty()) return;

        new HashSet<>(packets.keySet()).forEach(packet -> {
            if (System.currentTimeMillis() - packets.get(packet) >= ping.getValue()) {
                mc.getNetworkHandler().sendPacket(packet);
                packets.remove(packet);
            }
        });
    }

    @Override
    public void onDisable() {
        if (!packets.isEmpty()) {
            packets.keySet().forEach(packet -> mc.getNetworkHandler().sendPacket(packet));
            packets.clear();
        }
    }
}
